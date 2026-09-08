package com.ticketrush.event;

import com.ticketrush.event.dto.EventCreateRequest;
import com.ticketrush.event.dto.EventResponse;
import com.ticketrush.event.dto.EventUpdateRequest;
import com.ticketrush.exception.ResourceNotFoundException;
import com.ticketrush.user.User;
import com.ticketrush.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
public class EventServiceImpl implements EventService {

    private final EventRepository eventRepository;
    private final UserRepository userRepository;

    public EventServiceImpl(EventRepository eventRepository, UserRepository userRepository) {
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public EventResponse createEvent(EventCreateRequest request) {
        User organizer = userRepository.findById(request.organizerId())
                .orElseThrow(() -> new ResourceNotFoundException("Organizer (User) not found with id: " + request.organizerId()));

        Event event = new Event();
        event.setOrganizer(organizer);
        event.setTitle(request.title());
        event.setDescription(request.description());
        event.setCategory(request.category());
        event.setStatus(request.status());
        event.setCreatedAt(OffsetDateTime.now());
        
        event = eventRepository.save(event);
        return mapToResponse(event);
    }

    @Override
    @Transactional(readOnly = true)
    public EventResponse getEventById(Long id) {
        Event event = findEventOrThrow(id);
        return mapToResponse(event);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<EventResponse> getAllEvents(String category, Pageable pageable) {
        if (category != null && !category.isBlank()) {
            return eventRepository.findByCategory(category, pageable).map(this::mapToResponse);
        }
        return eventRepository.findAll(pageable).map(this::mapToResponse);
    }

    @Override
    @Transactional
    public EventResponse updateEvent(Long id, EventUpdateRequest request) {
        Event event = findEventOrThrow(id);
        
        if (request.title() != null) event.setTitle(request.title());
        if (request.description() != null) event.setDescription(request.description());
        if (request.category() != null) event.setCategory(request.category());
        if (request.status() != null) event.setStatus(request.status());
        
        event = eventRepository.save(event);
        return mapToResponse(event);
    }

    @Override
    @Transactional
    public void deleteEvent(Long id) {
        Event event = findEventOrThrow(id);
        eventRepository.delete(event);
    }

    private Event findEventOrThrow(Long id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + id));
    }

    private EventResponse mapToResponse(Event event) {
        return new EventResponse(
                event.getId(),
                event.getOrganizer().getId(),
                event.getTitle(),
                event.getDescription(),
                event.getCategory(),
                event.getStatus(),
                event.getCreatedAt()
        );
    }
}
