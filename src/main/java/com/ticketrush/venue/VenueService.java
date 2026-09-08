package com.ticketrush.venue;

import com.ticketrush.venue.dto.VenueCreateRequest;
import com.ticketrush.venue.dto.VenueResponse;
import com.ticketrush.venue.dto.VenueUpdateRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface VenueService {
    VenueResponse createVenue(VenueCreateRequest request);
    VenueResponse getVenueById(Long id);
    Page<VenueResponse> getAllVenues(Pageable pageable);
    VenueResponse updateVenue(Long id, VenueUpdateRequest request);
    void deleteVenue(Long id);
}
