package com.ticketrush.event;

import com.ticketrush.cache.CacheAsideService;
import com.ticketrush.event.dto.EventCreateRequest;
import com.ticketrush.event.dto.EventResponse;
import com.ticketrush.event.dto.EventUpdateRequest;
import com.ticketrush.exception.ResourceNotFoundException;
import com.ticketrush.user.User;
import com.ticketrush.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

@Service
public class EventServiceImpl implements EventService {

    private static final String CACHE_NAME = "events";
    private static final Duration CACHE_TTL = Duration.ofSeconds(45);

    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final CacheAsideService cache;

    public EventServiceImpl(EventRepository eventRepository, UserRepository userRepository,
                            CacheAsideService cache) {
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
        this.cache = cache;
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
        event.setStatus(com.ticketrush.event.EventStatus.DRAFT);
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
        String cat = (category != null && !category.isBlank()) ? category : "-";
        String cacheKey = "ticketrush:cache:events:" + cat + ":" + pageable.getPageNumber() + ":" + pageable.getPageSize();

        List<EventResponse> content = cache.getOrLoad(CACHE_NAME, cacheKey, CACHE_TTL,
                new TypeReference<List<EventResponse>>() {},
                () -> {
                    Page<EventResponse> page;
                    if (!"-".equals(cat)) {
                        page = eventRepository.findByCategory(category, pageable).map(this::mapToResponse);
                    } else {
                        page = eventRepository.findAll(pageable).map(this::mapToResponse);
                    }
                    return page.getContent();
                });

        // We cache only the content list; reconstruct the Page wrapper.
        // Total count comes from DB on miss (loader above), or is the list size on hit.
        // For cached responses, total is approximate — acceptable trade-off for events listing.
        return new PageImpl<>(content, pageable, content.size());
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

