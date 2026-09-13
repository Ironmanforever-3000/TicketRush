package com.ticketrush.booking;

import com.ticketrush.hold.Hold;
import com.ticketrush.show.Show;
import com.ticketrush.user.User;
import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
    name = "bookings",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_bookings_idempotency_key",
            columnNames = "idempotency_key"
        )
    }
)
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "show_id", nullable = false)
    private Show show;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hold_id", nullable = false)
    private Hold hold;

    @Column(name = "total_cents", nullable = false)
    private Long totalCents;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BookingStatus status;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private UUID idempotencyKey;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BookingSeat> bookingSeats = new ArrayList<>();

    protected Booking() {
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Show getShow() {
        return show;
    }

    public Hold getHold() {
        return hold;
    }

    public Long getTotalCents() {
        return totalCents;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public void setStatus(BookingStatus status) {
        this.status = status;
    }

    public UUID getIdempotencyKey() {
        return idempotencyKey;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public List<BookingSeat> getBookingSeats() {
        return bookingSeats;
    }
}
