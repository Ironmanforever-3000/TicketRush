package com.ticketrush.ticket;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class TicketCodeServiceTest {
    final TicketCodeService svc = new TicketCodeService("0123456789abcdef0123456789abcdef");

    @Test
    void roundTrip() {
        UUID id = UUID.randomUUID();
        assertThat(svc.verify(svc.encode(7L, id))).contains(new TicketCodeService.Parsed(7L, id));
    }

    @Test
    void editedShowIdOrSignatureFails() {
        String code = svc.encode(7L, UUID.randomUUID());
        assertThat(svc.verify(code.replace("TR1.7.", "TR1.8."))).isEmpty();
        assertThat(svc.verify(code.substring(0, code.length() - 2) + "AA")).isEmpty();
    }

    @Test
    void otherSecretFails() {
        String code = new TicketCodeService("ffffffffffffffffffffffffffffffff").encode(7L, UUID.randomUUID());
        assertThat(svc.verify(code)).isEmpty();
    }

    @Test
    void garbageFails() {
        for (String s : List.of("", "x", "TR1.1.2.3", "TR1.a.b.c", "TR1.1." + UUID.randomUUID() + ".!!!")) {
            assertThat(svc.verify(s)).isEmpty();
        }
    }
}
