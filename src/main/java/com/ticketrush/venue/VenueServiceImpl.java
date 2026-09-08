package com.ticketrush.venue;

import com.ticketrush.exception.ResourceNotFoundException;
import com.ticketrush.venue.dto.VenueCreateRequest;
import com.ticketrush.venue.dto.VenueResponse;
import com.ticketrush.venue.dto.VenueUpdateRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
public class VenueServiceImpl implements VenueService {

    private final VenueRepository venueRepository;

    public VenueServiceImpl(VenueRepository venueRepository) {
        this.venueRepository = venueRepository;
    }

    @Override
    @Transactional
    public VenueResponse createVenue(VenueCreateRequest request) {
        Venue venue = new Venue(request.name(), request.city(), request.layoutJson(), OffsetDateTime.now());
        venue = venueRepository.save(venue);
        return mapToResponse(venue);
    }

    @Override
    @Transactional(readOnly = true)
    public VenueResponse getVenueById(Long id) {
        Venue venue = findVenueOrThrow(id);
        return mapToResponse(venue);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<VenueResponse> getAllVenues(Pageable pageable) {
        return venueRepository.findAll(pageable).map(this::mapToResponse);
    }

    @Override
    @Transactional
    public VenueResponse updateVenue(Long id, VenueUpdateRequest request) {
        Venue venue = findVenueOrThrow(id);
        if (request.name() != null) venue.setName(request.name());
        if (request.city() != null) venue.setCity(request.city());
        if (request.layoutJson() != null) venue.setLayoutJson(request.layoutJson());
        venue = venueRepository.save(venue);
        return mapToResponse(venue);
    }

    @Override
    @Transactional
    public void deleteVenue(Long id) {
        Venue venue = findVenueOrThrow(id);
        venueRepository.delete(venue);
    }

    private Venue findVenueOrThrow(Long id) {
        return venueRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Venue not found with id: " + id));
    }

    private VenueResponse mapToResponse(Venue venue) {
        return new VenueResponse(venue.getId(), venue.getName(), venue.getCity(), venue.getLayoutJson(), venue.getCreatedAt());
    }
}
