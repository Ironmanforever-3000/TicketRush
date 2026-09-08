package com.ticketrush.show;

import com.ticketrush.seat.dto.SeatMapResponse;
import com.ticketrush.show.dto.ShowCreateRequest;
import com.ticketrush.show.dto.ShowResponse;
import com.ticketrush.show.dto.ShowUpdateRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ShowService {
    ShowResponse createShow(ShowCreateRequest request);
    ShowResponse getShowById(Long id);
    Page<ShowResponse> getAllShows(Pageable pageable);
    ShowResponse updateShow(Long id, ShowUpdateRequest request);
    void deleteShow(Long id);
    SeatMapResponse getSeatMap(Long id);
}
