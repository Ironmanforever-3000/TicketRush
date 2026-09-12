package com.ticketrush.hold;

import com.ticketrush.exception.BadRequestException;
import com.ticketrush.exception.ResourceNotFoundException;
import com.ticketrush.hold.dto.HoldRequest;
import com.ticketrush.hold.dto.HoldResponse;
import com.ticketrush.seat.SeatRepository;
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

    public HoldServiceImpl(
            SeatRepository seatRepository,
            ShowRepository showRepository,
            UserRepository userRepository,
            HoldRepository holdRepository
    ) {
        this.seatRepository = seatRepository;
        this.showRepository = showRepository;
        this.userRepository = userRepository;
        this.holdRepository = holdRepository;
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

        OffsetDateTime expiresAt = OffsetDateTime.now().plusMinutes(5);

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
        hold.setCreatedAt(OffsetDateTime.now());

        Hold savedHold = holdRepository.save(hold);

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
