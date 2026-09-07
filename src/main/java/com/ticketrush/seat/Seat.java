package com.ticketrush.seat;

import com.ticketrush.show.Show;
import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "seats",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_show_seat",
            columnNames = {
                "show_id",
                "row_label",
                "seat_number"
            }
        )
    }
)
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "show_id", nullable = false)
    private Show show;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tier_id", nullable = false)
    private SeatTier tier;

    @Column(name = "row_label", nullable = false, length = 20)
    private String rowLabel;

    @Column(name = "seat_number", nullable = false)
    private Integer seatNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SeatStatus status;

    @Column(name = "held_by")
    private UUID heldBy;

    @Column(name = "hold_expires_at")
    private OffsetDateTime holdExpiresAt;

    @Version
    @Column(nullable = false)
    private Long version;

    public Seat() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Show getShow() { return show; }
    public void setShow(Show show) { this.show = show; }
    public SeatTier getTier() { return tier; }
    public void setTier(SeatTier tier) { this.tier = tier; }
    public String getRowLabel() { return rowLabel; }
    public void setRowLabel(String rowLabel) { this.rowLabel = rowLabel; }
    public Integer getSeatNumber() { return seatNumber; }
    public void setSeatNumber(Integer seatNumber) { this.seatNumber = seatNumber; }
    public SeatStatus getStatus() { return status; }
    public void setStatus(SeatStatus status) { this.status = status; }
    public UUID getHeldBy() { return heldBy; }
    public void setHeldBy(UUID heldBy) { this.heldBy = heldBy; }
    public OffsetDateTime getHoldExpiresAt() { return holdExpiresAt; }
    public void setHoldExpiresAt(OffsetDateTime holdExpiresAt) { this.holdExpiresAt = holdExpiresAt; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
