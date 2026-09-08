package com.ticketrush.show;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.ticketrush.event.Event;
import com.ticketrush.event.EventRepository;
import com.ticketrush.exception.BadRequestException;
import com.ticketrush.exception.ResourceNotFoundException;
import com.ticketrush.seat.Seat;
import com.ticketrush.seat.SeatGenerator;
import com.ticketrush.seat.SeatRepository;
import com.ticketrush.seat.SeatTier;
import com.ticketrush.seat.SeatTierRepository;
import com.ticketrush.seat.dto.SeatMapResponse;
import com.ticketrush.seat.dto.SeatResponse;
import com.ticketrush.show.dto.SeatTierRequest;
import com.ticketrush.show.dto.ShowCreateRequest;
import com.ticketrush.show.dto.ShowResponse;
import com.ticketrush.show.dto.ShowUpdateRequest;
import com.ticketrush.venue.Venue;
import com.ticketrush.venue.VenueRepository;
import com.ticketrush.venue.dto.VenueLayout;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ShowServiceImpl implements ShowService {

    private final ShowRepository showRepository;
    private final EventRepository eventRepository;
    private final VenueRepository venueRepository;
    private final SeatTierRepository seatTierRepository;
    private final SeatRepository seatRepository;
    private final SeatGenerator seatGenerator;
    private final ObjectMapper objectMapper;

    public ShowServiceImpl(ShowRepository showRepository, EventRepository eventRepository, VenueRepository venueRepository,
                           SeatTierRepository seatTierRepository, SeatRepository seatRepository,
                           SeatGenerator seatGenerator, ObjectMapper objectMapper) {
        this.showRepository = showRepository;
        this.eventRepository = eventRepository;
        this.venueRepository = venueRepository;
        this.seatTierRepository = seatTierRepository;
        this.seatRepository = seatRepository;
        this.seatGenerator = seatGenerator;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public ShowResponse createShow(ShowCreateRequest request) {
        Event event = eventRepository.findById(request.eventId())
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + request.eventId()));
        
        Venue venue = venueRepository.findById(request.venueId())
                .orElseThrow(() -> new ResourceNotFoundException("Venue not found with id: " + request.venueId()));

        if (request.saleOpensAt().isAfter(request.startsAt())) {
            throw new BadRequestException("saleOpensAt cannot be after startsAt");
        }
        if (request.saleClosesAt() != null && request.startsAt().isBefore(request.saleClosesAt())) {
            throw new BadRequestException("startsAt cannot be before saleClosesAt");
        }

        Show show = new Show();
        show.setEvent(event);
        show.setVenue(venue);
        show.setStartsAt(request.startsAt());
        show.setSaleOpensAt(request.saleOpensAt());
        show.setSaleClosesAt(request.saleClosesAt());
        show.setStatus(request.status());
        show = showRepository.save(show);

        Map<String, SeatTier> tierMap = new HashMap<>();
        for (SeatTierRequest tierReq : request.tiers()) {
            SeatTier tier = new SeatTier();
            tier.setShow(show);
            tier.setName(tierReq.name());
            tier.setPriceCents(tierReq.priceCents());
            tier.setCurrency(tierReq.currency());
            tier = seatTierRepository.save(tier);
            tierMap.put(tier.getName(), tier);
        }

        VenueLayout layout;
        try {
            layout = objectMapper.readValue(venue.getLayoutJson(), VenueLayout.class);
        } catch (JacksonException e) {
            throw new BadRequestException("Invalid venue layout JSON");
        }

        List<Seat> seats = seatGenerator.generate(show, layout, tierMap);
        seatRepository.saveAll(seats);

        return mapToResponse(show);
    }

    @Override
    @Transactional(readOnly = true)
    public ShowResponse getShowById(Long id) {
        Show show = findShowOrThrow(id);
        return mapToResponse(show);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ShowResponse> getAllShows(Pageable pageable) {
        return showRepository.findAll(pageable).map(this::mapToResponse);
    }

    @Override
    @Transactional
    public ShowResponse updateShow(Long id, ShowUpdateRequest request) {
        Show show = findShowOrThrow(id);
        if (request.startsAt() != null) show.setStartsAt(request.startsAt());
        if (request.saleOpensAt() != null) show.setSaleOpensAt(request.saleOpensAt());
        if (request.saleClosesAt() != null) show.setSaleClosesAt(request.saleClosesAt());
        if (request.status() != null) show.setStatus(request.status());
        show = showRepository.save(show);
        return mapToResponse(show);
    }

    @Override
    @Transactional
    public void deleteShow(Long id) {
        Show show = findShowOrThrow(id);
        showRepository.delete(show);
    }

    @Override
    @Transactional(readOnly = true)
    public SeatMapResponse getSeatMap(Long id) {
        Show show = findShowOrThrow(id);
        List<Seat> seats = seatRepository.findByShowIdOrderByRowLabelAscSeatNumberAsc(id);
        List<SeatResponse> seatResponses = seats.stream()
                .map(seat -> new SeatResponse(
                        seat.getId(),
                        seat.getRowLabel(),
                        seat.getSeatNumber(),
                        seat.getStatus(),
                        seat.getTier().getName(),
                        seat.getTier().getPriceCents(),
                        seat.getTier().getCurrency()
                )).toList();
        return new SeatMapResponse(id, seatResponses);
    }

    private Show findShowOrThrow(Long id) {
        return showRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Show not found with id: " + id));
    }

    private ShowResponse mapToResponse(Show show) {
        return new ShowResponse(
                show.getId(),
                show.getEvent().getId(),
                show.getEvent().getTitle(),
                show.getVenue().getId(),
                show.getVenue().getName(),
                show.getStartsAt(),
                show.getSaleOpensAt(),
                show.getSaleClosesAt(),
                show.getStatus()
        );
    }
}

