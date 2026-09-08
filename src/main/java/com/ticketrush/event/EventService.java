package com.ticketrush.event;

import com.ticketrush.event.dto.EventCreateRequest;
import com.ticketrush.event.dto.EventResponse;
import com.ticketrush.event.dto.EventUpdateRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface EventService {
    EventResponse createEvent(EventCreateRequest request);
    EventResponse getEventById(Long id);
    Page<EventResponse> getAllEvents(String category, Pageable pageable);
    EventResponse updateEvent(Long id, EventUpdateRequest request);
    void deleteEvent(Long id);
}
