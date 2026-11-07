package com.ticketrush.websocket;

import com.ticketrush.AbstractIntegrationTest;
import com.ticketrush.auth.Role;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebSocketAuthIntegrationTest extends AbstractIntegrationTest {

    @LocalServerPort
    int port;

    @Test
    void connectWithoutAuthorizationHeaderIsRejected() {
        var client = new WebSocketStompClient(new StandardWebSocketClient());
        var connectHeaders = new StompHeaders();   // no Authorization header
        assertThatThrownBy(() ->
                client.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), connectHeaders,
                        new StompSessionHandlerAdapter() {}).get(5, TimeUnit.SECONDS)
        ).isNotNull();
    }

    @Test
    void connectWithValidTokenAndSubscribeSucceeds() throws Exception {
        TestUser customer = createUser(Role.CUSTOMER);
        var connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + customer.token());
        var client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new JacksonJsonMessageConverter());
        StompSession session = client.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(),
                connectHeaders, new StompSessionHandlerAdapter() {}).get(5, TimeUnit.SECONDS);
        assertThat(session.isConnected()).isTrue();
        session.subscribe("/topic/shows/101/seats", new StompSessionHandlerAdapter() {});
        session.disconnect();
    }
}
