package com.ticketrush.hold;

import com.ticketrush.exception.BadRequestException;
import com.ticketrush.exception.BusinessRuleException;
import com.ticketrush.exception.ResourceNotFoundException;
import com.ticketrush.hold.dto.HoldRequest;
import com.ticketrush.hold.dto.HoldResponse;
import com.ticketrush.ratelimit.QuotaLockService;
import com.ticketrush.seat.SeatRepository;
import com.ticketrush.seat.SeatStateOutboxWriter;
import com.ticketrush.seat.SeatStatus;
import com.ticketrush.seat.SeatUnavailableException;
import com.ticketrush.show.Show;
import com.ticketrush.show.ShowRepository;
import com.ticketrush.user.User;
import com.ticketrush.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class HoldServiceImpl implements HoldService {

    private final SeatRepository seatRepository;
    private final ShowRepository showRepository;
    private final UserRepository userRepository;
    private final HoldRepository holdRepository;
    private final SeatStateOutboxWriter seatStateOutboxWriter;
    private final QuotaLockService quotaLockService;

    public HoldServiceImpl(
            SeatRepository seatRepository,
            ShowRepository showRepository,
            UserRepository userRepository,
            HoldRepository holdRepository,
            SeatStateOutboxWriter seatStateOutboxWriter,
            QuotaLockService quotaLockService
    ) {
        this.seatRepository = seatRepository;
        this.showRepository = showRepository;
        this.userRepository = userRepository;
        this.holdRepository = holdRepository;
        this.seatStateOutboxWriter = seatStateOutboxWriter;
        this.quotaLockService = quotaLockService;
    }

    @Override
    @Transactional
    public HoldResponse createHold(
            Long showId,
            HoldRequest request
    ) {
        Show show = showRepository.findById(showId)
                .orElseThrow(() -> new ResourceNotFoundException("Show not found with id: " + showId));

        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + request.userId()));

        if (request.seatIds() == null || request.seatIds().isEmpty()) {
            throw new BadRequestException("At least one seat is required");
        }

        List<Long> seatIds = request.seatIds()
                .stream()
                .distinct()
                .sorted()
                .toList();

        // 1. Transactional advisory locks in deterministic order (user first, then user+show)
        quotaLockService.lockUserHolds(user.getId());
        quotaLockService.lockUserShowSeats(user.getId(), showId);

        OffsetDateTime now = OffsetDateTime.now();

        // 2. Business rule 1: Max 3 active holds per user across all shows
        long activeHolds = holdRepository.countActiveHolds(user.getId(), now);
        if (activeHolds >= 3) {
            throw new BusinessRuleException("Maximum 3 active holds are allowed.");
        }

        // 3. Business rule 2: Max 6 seats held per user per show
        long activeHeldSeats = seatRepository.countActiveHeldSeats(showId, user.getId(), now);
        if (activeHeldSeats + seatIds.size() > 6) {
            throw new BusinessRuleException("You can hold a maximum of 6 seats per show.");
        }

        OffsetDateTime expiresAt = now.plusMinutes(5);

        // Lock seats in deterministic order to prevent deadlocks
        seatRepository.lockSeatsForUpdate(seatIds);

        int updated = seatRepository.holdAvailableSeats(
                seatIds,
                showId,
                user.getId(),
                expiresAt,
                SeatStatus.AVAILABLE,
                SeatStatus.HELD
        );

        if (updated != seatIds.size()) {
            throw new SeatUnavailableException("One or more seats are unavailable");
        }

        Hold hold = new Hold();
        hold.setShow(show);
        hold.setUser(user);
        hold.setSeatIds(seatIds);
        hold.setExpiresAt(expiresAt);
        hold.setStatus(HoldStatus.ACTIVE);
        hold.setCreatedAt(now);

        Hold savedHold = holdRepository.save(hold);
        seatStateOutboxWriter.write(showId, seatIds, "HELD");

        return new HoldResponse(
                savedHold.getId(),
                show.getId(),
                user.getId(),
                seatIds,
                savedHold.getExpiresAt(),
                savedHold.getStatus().name()
        );
    }
}
