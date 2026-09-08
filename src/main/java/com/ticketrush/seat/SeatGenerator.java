package com.ticketrush.seat;

import com.ticketrush.show.Show;
import com.ticketrush.venue.dto.VenueLayout;
import com.ticketrush.venue.dto.VenueRow;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class SeatGenerator {

    public List<Seat> generate(Show show, VenueLayout layout, Map<String, SeatTier> tierMap) {
        List<Seat> generatedSeats = new ArrayList<>();

        for (VenueRow row : layout.rows()) {
            SeatTier tier = tierMap.get(row.tier());
            if (tier == null) {
                throw new IllegalArgumentException("Tier not found in show request for layout tier: " + row.tier());
            }

            for (int i = 1; i <= row.seats(); i++) {
                Seat seat = new Seat();
                seat.setShow(show);
                seat.setTier(tier);
                seat.setRowLabel(row.label());
                seat.setSeatNumber(i);
                seat.setStatus(SeatStatus.AVAILABLE);
                generatedSeats.add(seat);
            }
        }

        return generatedSeats;
    }
}
