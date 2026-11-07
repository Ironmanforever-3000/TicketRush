package com.ticketrush.websocket;

import com.ticketrush.TestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class WebSocketConfigTest {

    @LocalServerPort
    int port;

    @Autowired
    SimpMessagingTemplate messagingTemplate;

    @Autowired
    com.ticketrush.security.JwtService jwtService;

    @Test
    void aSubscribedClientReceivesABroadcastOnItsTopic() throws Exception {
        var client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new StringMessageConverter());

        var received = new CompletableFuture<String>();
        String token = jwtService.generateToken("test@example.com", "CUSTOMER", 123L);
        var connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + token);

        StompSession session = client.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(),
                connectHeaders, new StompSessionHandlerAdapter() {}).get(5, TimeUnit.SECONDS);

        session.subscribe("/topic/shows/101/seats", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) { return String.class; }
            @Override
            public void handleFrame(StompHeaders headers, Object payload) { received.complete((String) payload); }
        });
        Thread.sleep(200);   // let the SUBSCRIBE frame register before we publish

        messagingTemplate.convertAndSend("/topic/shows/101/seats", "{\"seatId\":\"A2\",\"status\":\"HELD\"}");

        assertThat(received.get(5, TimeUnit.SECONDS)).contains("HELD");
        session.disconnect();
    }

    @Test
    void handshakeFromADisallowedOriginIsRejected() {
        var client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new StringMessageConverter());

        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add("Origin", "http://evil.example");

        assertThatThrownBy(() ->
                client.connectAsync("ws://localhost:" + port + "/ws", headers,
                        new StompSessionHandlerAdapter() {}).get(5, TimeUnit.SECONDS)
        ).isInstanceOf(ExecutionException.class);
    }
}
