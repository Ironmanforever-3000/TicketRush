package com.ticketrush.realtime;

import com.ticketrush.seat.SeatStateChangedEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@Profile("!worker")
public class RedisSeatSubscriber implements MessageListener {
    private static final Logger log = LoggerFactory.getLogger(RedisSeatSubscriber.class);
    private final ObjectMapper mapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final Counter malformedMessageCounter;

    public RedisSeatSubscriber(ObjectMapper mapper, SimpMessagingTemplate messagingTemplate, MeterRegistry registry) {
        this.mapper = mapper;
        this.messagingTemplate = messagingTemplate;
        this.malformedMessageCounter = Counter.builder("ticketrush.websocket.seat_update.malformed")
                .description("Malformed seat-update messages received via Redis Pub/Sub")
                .register(registry);
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            SeatStateChangedEvent event = mapper.readValue(message.getBody(), SeatStateChangedEvent.class);
            messagingTemplate.convertAndSend("/topic/shows/" + event.showId() + "/seats", event);
        } catch (Exception ex) {
            malformedMessageCounter.increment();
            log.error("Failed to process seat-update Redis message: {}", ex.toString(), ex);
        }
    }
}
