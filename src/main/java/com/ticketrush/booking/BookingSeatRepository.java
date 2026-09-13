package com.ticketrush.booking;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingSeatRepository extends JpaRepository<BookingSeat, BookingSeatId> {
}
