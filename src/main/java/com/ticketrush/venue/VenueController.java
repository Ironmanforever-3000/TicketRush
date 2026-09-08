package com.ticketrush.venue;

import com.ticketrush.venue.dto.VenueCreateRequest;
import com.ticketrush.venue.dto.VenueResponse;
import com.ticketrush.venue.dto.VenueUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/venues")
public class VenueController {

    private final VenueService venueService;

    public VenueController(VenueService venueService) {
        this.venueService = venueService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VenueResponse createVenue(@Valid @RequestBody VenueCreateRequest request) {
        return venueService.createVenue(request);
    }

    @GetMapping("/{id}")
    public VenueResponse getVenue(@PathVariable Long id) {
        return venueService.getVenueById(id);
    }

    @GetMapping
    public Page<VenueResponse> getAllVenues(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        size = Math.min(size, 100); // Cap size at 100 to prevent abusive values
        Pageable pageable = PageRequest.of(page, size);
        return venueService.getAllVenues(pageable);
    }

    @PatchMapping("/{id}")
    public VenueResponse updateVenue(@PathVariable Long id, @Valid @RequestBody VenueUpdateRequest request) {
        return venueService.updateVenue(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteVenue(@PathVariable Long id) {
        venueService.deleteVenue(id);
    }
}
