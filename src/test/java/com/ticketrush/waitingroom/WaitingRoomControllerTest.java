package com.ticketrush.waitingroom;

import com.ticketrush.AbstractIntegrationTest;
import com.ticketrush.auth.Role;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class WaitingRoomControllerTest extends AbstractIntegrationTest {

    @Test
    void joinPositionAdmitAndOneTimeValidateWorkflow() throws Exception {
        Fixture f = bookedShow(1);
        TestUser secondCustomer = createUser(Role.CUSTOMER);

        // Two customers join the waiting room
        mvc.perform(post("/api/v1/shows/{showId}/waiting-room/join", f.showId())
                        .header("Authorization", bearer(f.customer().token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queueNumber").value(1));

        mvc.perform(post("/api/v1/shows/{showId}/waiting-room/join", f.showId())
                        .header("Authorization", bearer(secondCustomer.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queueNumber").value(2));

        mvc.perform(get("/api/v1/shows/{showId}/waiting-room/position", f.showId())
                        .header("Authorization", bearer(secondCustomer.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inQueue").value(true))
                .andExpect(jsonPath("$.position").value(2));

        // CUSTOMER cannot admit users
        mvc.perform(post("/api/v1/shows/{showId}/waiting-room/admit?count=1", f.showId())
                        .header("Authorization", bearer(f.customer().token())))
                .andExpect(status().isForbidden());

        // Owning ORGANIZER admits first customer
        MvcResult admitRes = mvc.perform(post("/api/v1/shows/{showId}/waiting-room/admit?count=1", f.showId())
                        .header("Authorization", bearer(f.organizer().token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value(f.customer().id()))
                .andReturn();

        String token = objectMapper.readTree(admitRes.getResponse().getContentAsString()).get(0).get("token").asString();
        assertThat(token).isNotBlank();

        // Second customer moves up to position 1
        mvc.perform(get("/api/v1/shows/{showId}/waiting-room/position", f.showId())
                        .header("Authorization", bearer(secondCustomer.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position").value(1));

        // First customer validates token once (true), replay is consumed (false)
        String validateBody = objectMapper.writeValueAsString(new WaitingRoomController.ValidateTokenRequest(token));
        mvc.perform(post("/api/v1/shows/{showId}/waiting-room/validate", f.showId())
                        .header("Authorization", bearer(f.customer().token()))
                        .contentType(APPLICATION_JSON)
                        .content(validateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));

        mvc.perform(post("/api/v1/shows/{showId}/waiting-room/validate", f.showId())
                        .header("Authorization", bearer(f.customer().token()))
                        .contentType(APPLICATION_JSON)
                        .content(validateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false));
    }
}
