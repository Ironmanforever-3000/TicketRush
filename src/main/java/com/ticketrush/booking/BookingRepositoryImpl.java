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

        List<?> result = entityManager
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
                    now()
                )
                ON CONFLICT (idempotency_key)
                DO NOTHING
                RETURNING id
                """)
            .setParameter("userId", userId)
            .setParameter("showId", showId)
            .setParameter("holdId", holdId)
            .setParameter("totalCents", totalCents)
            .setParameter("idempotencyKey", idempotencyKey)
            .getResultList();

        if (result.isEmpty()) {
            return Optional.empty();
        }

        Number id = (Number) result.get(0);
        return Optional.of(id.longValue());
    }
}
