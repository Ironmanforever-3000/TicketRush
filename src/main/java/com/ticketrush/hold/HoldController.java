package com.ticketrush.hold;

import com.ticketrush.hold.dto.HoldRequest;
import com.ticketrush.hold.dto.HoldResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/shows/{showId}/holds")
public class HoldController {

    private final HoldService holdService;

    public HoldController(HoldService holdService) {
        this.holdService = holdService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HoldResponse createHold(
            @PathVariable Long showId,
            @RequestBody @Valid HoldRequest request
    ) {
        return holdService.createHold(showId, request);
    }
}
