package com.ticketrush;

import com.ticketrush.event.Event;
import com.ticketrush.event.EventStatus;
import com.ticketrush.show.Show;
import com.ticketrush.show.ShowStatus;
import com.ticketrush.venue.Venue;
import com.ticketrush.venue.dto.VenueLayout;
import com.ticketrush.venue.dto.VenueRow;
import com.ticketrush.seat.SeatGenerator;
import com.ticketrush.seat.Seat;
import com.ticketrush.seat.SeatTier;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

public class SeatGeneratorTest {

    @Test
    void test500SeatGeneration() {
        SeatGenerator generator = new SeatGenerator();
        
        Show show = new Show();
        VenueLayout layout = new VenueLayout(List.of(
                new VenueRow("A", 100, "VIP"),
                new VenueRow("B", 100, "VIP"),
                new VenueRow("C", 100, "REGULAR"),
                new VenueRow("D", 100, "REGULAR"),
                new VenueRow("E", 100, "REGULAR")
        ));
        
        SeatTier vip = new SeatTier();
        vip.setName("VIP");
        
        SeatTier regular = new SeatTier();
        regular.setName("REGULAR");
        
        Map<String, SeatTier> tierMap = Map.of(
                "VIP", vip,
                "REGULAR", regular
        );
        
        List<Seat> seats = generator.generate(show, layout, tierMap);
        
        assertThat(seats).hasSize(500);
        assertThat(seats.stream().filter(s -> s.getRowLabel().equals("A")).count()).isEqualTo(100);
        assertThat(seats.stream().filter(s -> s.getTier().getName().equals("VIP")).count()).isEqualTo(200);
    }
}
