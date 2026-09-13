package com.ticketrush.booking;

import com.ticketrush.seat.Seat;
import jakarta.persistence.*;

@Entity
@Table(name = "booking_seats")
public class BookingSeat {

    @EmbeddedId
    private BookingSeatId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("bookingId")
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("seatId")
    @JoinColumn(name = "seat_id")
    private Seat seat;

    @Column(name = "price_cents", nullable = false)
    private Long priceCents;

    protected BookingSeat() {
    }

    public BookingSeatId getId() {
        return id;
    }

    public void setId(BookingSeatId id) {
        this.id = id;
    }

    public Booking getBooking() {
        return booking;
    }

    public void setBooking(Booking booking) {
        this.booking = booking;
    }

    public Seat getSeat() {
        return seat;
    }

    public void setSeat(Seat seat) {
        this.seat = seat;
    }

    public Long getPriceCents() {
        return priceCents;
    }

    public void setPriceCents(Long priceCents) {
        this.priceCents = priceCents;
    }
}
