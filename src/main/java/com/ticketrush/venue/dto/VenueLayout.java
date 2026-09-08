package com.ticketrush.venue.dto;

import java.util.List;

public record VenueLayout(
        List<VenueRow> rows
) {}
