package com.ticketrush.show;

import com.ticketrush.seat.dto.SeatMapResponse;
import com.ticketrush.show.dto.ShowCreateRequest;
import com.ticketrush.show.dto.ShowResponse;
import com.ticketrush.show.dto.ShowUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/shows")
public class ShowController {

    private final ShowService showService;

    public ShowController(ShowService showService) {
        this.showService = showService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShowResponse createShow(@Valid @RequestBody ShowCreateRequest request) {
        return showService.createShow(request);
    }

    @GetMapping("/{id}")
    public ShowResponse getShow(@PathVariable Long id) {
        return showService.getShowById(id);
    }

    @GetMapping
    public Page<ShowResponse> getAllShows(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        size = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, size);
        return showService.getAllShows(pageable);
    }

    @PatchMapping("/{id}")
    public ShowResponse updateShow(@PathVariable Long id, @Valid @RequestBody ShowUpdateRequest request) {
        return showService.updateShow(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteShow(@PathVariable Long id) {
        showService.deleteShow(id);
    }

    @GetMapping("/{id}/seatmap")
    public SeatMapResponse getSeatMap(@PathVariable Long id) {
        return showService.getSeatMap(id);
    }
}
