package com.ticketrush.ratelimit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class QuotaLockService {

    private final JdbcTemplate jdbcTemplate;
    private final ConcurrentMap<String, ReentrantLock> inMemoryLocks = new ConcurrentHashMap<>();
    private final boolean isPostgres;

    public QuotaLockService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        boolean postgres = true;
        try (var conn = jdbcTemplate.getDataSource().getConnection()) {
            postgres = conn.getMetaData().getDatabaseProductName().toLowerCase().contains("postgres");
        } catch (Exception ignored) {
        }
        this.isPostgres = postgres;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockUserHolds(Long userId) {
        lock("holds:user:" + userId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockUserShowSeats(Long userId, Long showId) {
        lock("holds:user:" + userId + ":show:" + showId);
    }

    private void lock(String lockKey) {
        if (isPostgres) {
            jdbcTemplate.execute(
                    "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                    (PreparedStatementCallback<Void>) ps -> {
                        ps.setString(1, lockKey);
                        ps.execute();
                        return null;
                    }
            );
        } else {
            ReentrantLock lock = inMemoryLocks.computeIfAbsent(lockKey, k -> new ReentrantLock());
            lock.lock();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
            });
        }
    }
}
