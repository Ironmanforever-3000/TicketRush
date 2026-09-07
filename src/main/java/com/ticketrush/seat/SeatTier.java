package com.ticketrush.seat;

import com.ticketrush.show.Show;
import jakarta.persistence.*;

@Entity
@Table(
    name = "seat_tiers",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_seat_tier_name",
            columnNames = {"show_id", "name"}
        )
    }
)
public class SeatTier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "show_id", nullable = false)
    private Show show;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "price_cents", nullable = false)
    private Long priceCents;

    @Column(nullable = false, length = 3)
    private String currency;

    public SeatTier() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Show getShow() { return show; }
    public void setShow(Show show) { this.show = show; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getPriceCents() { return priceCents; }
    public void setPriceCents(Long priceCents) { this.priceCents = priceCents; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
}
