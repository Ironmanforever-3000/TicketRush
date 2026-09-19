package com.ticketrush.booking;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class BookingRepositoryImpl implements BookingRepositoryCustom {

    private final EntityManager entityManager;

    public BookingRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Optional<Long> insertIfAbsent(
            Long userId,
            Long showId,
            Long holdId,
            Long totalCents,
            UUID idempotencyKey) {

        List<?> existing = entityManager
            .createNativeQuery("SELECT id FROM bookings WHERE idempotency_key = :idempotencyKey")
            .setParameter("idempotencyKey", idempotencyKey)
            .getResultList();

        if (!existing.isEmpty()) {
            Number id = (Number) existing.get(0);
            return Optional.of(id.longValue());
        }

        try {
            entityManager
                .createNativeQuery("""
                    INSERT INTO bookings (
                        user_id,
                        show_id,
                        hold_id,
                        total_cents,
                        status,
                        idempotency_key,
                        created_at
                    )
                    VALUES (
                        :userId,
                        :showId,
                        :holdId,
                        :totalCents,
                        'PENDING',
                        :idempotencyKey,
                        CURRENT_TIMESTAMP
                    )
                    """)
                .setParameter("userId", userId)
                .setParameter("showId", showId)
                .setParameter("holdId", holdId)
                .setParameter("totalCents", totalCents)
                .setParameter("idempotencyKey", idempotencyKey)
                .executeUpdate();
            entityManager.flush();
        } catch (RuntimeException duplicate) {
            List<?> existingAfterInsert = entityManager
                .createNativeQuery("SELECT id FROM bookings WHERE idempotency_key = :idempotencyKey")
                .setParameter("idempotencyKey", idempotencyKey)
                .getResultList();

            if (!existingAfterInsert.isEmpty()) {
                Number id = (Number) existingAfterInsert.get(0);
                return Optional.of(id.longValue());
            }
            throw duplicate;
        }

        List<?> inserted = entityManager
            .createNativeQuery("SELECT id FROM bookings WHERE idempotency_key = :idempotencyKey")
            .setParameter("idempotencyKey", idempotencyKey)
            .getResultList();

        if (inserted.isEmpty()) {
            return Optional.empty();
        }

        Number id = (Number) inserted.get(0);
        return Optional.of(id.longValue());
    }
}
