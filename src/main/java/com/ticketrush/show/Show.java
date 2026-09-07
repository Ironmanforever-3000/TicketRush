package com.ticketrush.show;

import com.ticketrush.event.Event;
import com.ticketrush.venue.Venue;
import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "shows")
public class Show {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_id", nullable = false)
    private Venue venue;

    @Column(name = "starts_at", nullable = false)
    private OffsetDateTime startsAt;

    @Column(name = "sale_opens_at", nullable = false)
    private OffsetDateTime saleOpensAt;

    @Column(name = "sale_closes_at")
    private OffsetDateTime saleClosesAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ShowStatus status;

    public Show() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Event getEvent() { return event; }
    public void setEvent(Event event) { this.event = event; }
    public Venue getVenue() { return venue; }
    public void setVenue(Venue venue) { this.venue = venue; }
    public OffsetDateTime getStartsAt() { return startsAt; }
    public void setStartsAt(OffsetDateTime startsAt) { this.startsAt = startsAt; }
    public OffsetDateTime getSaleOpensAt() { return saleOpensAt; }
    public void setSaleOpensAt(OffsetDateTime saleOpensAt) { this.saleOpensAt = saleOpensAt; }
    public OffsetDateTime getSaleClosesAt() { return saleClosesAt; }
    public void setSaleClosesAt(OffsetDateTime saleClosesAt) { this.saleClosesAt = saleClosesAt; }
    public ShowStatus getStatus() { return status; }
    public void setStatus(ShowStatus status) { this.status = status; }
}
