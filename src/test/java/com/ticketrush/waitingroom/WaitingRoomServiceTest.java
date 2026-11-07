package com.ticketrush.waitingroom;

import com.ticketrush.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WaitingRoomServiceTest extends AbstractIntegrationTest {

    @Autowired
    private WaitingRoomService waitingRoomService;

    @Autowired
    private WaitingRoomTokenService tokenService;

    @Test
    void usersJoinQueueInSequentialOrderAndAreAdmittedByPosition() {
        Long showId = 999L;
        Long user1 = 101L;
        Long user2 = 102L;
        Long user3 = 103L;

        long pos1 = waitingRoomService.joinQueue(showId, user1);
        long pos2 = waitingRoomService.joinQueue(showId, user2);
        long pos3 = waitingRoomService.joinQueue(showId, user3);

        assertThat(pos1).isLessThan(pos2);
        assertThat(pos2).isLessThan(pos3);

        assertThat(waitingRoomService.getQueuePosition(showId, user1)).isEqualTo(1L);
        assertThat(waitingRoomService.getQueuePosition(showId, user2)).isEqualTo(2L);
        assertThat(waitingRoomService.getQueuePosition(showId, user3)).isEqualTo(3L);

        // Admit first 2 users
        List<WaitingRoomService.AdmittedUser> admitted = waitingRoomService.admitNextBatch(showId, 2);
        assertThat(admitted).hasSize(2);
        assertThat(admitted.get(0).userId()).isEqualTo(user1);
        assertThat(admitted.get(1).userId()).isEqualTo(user2);

        // Position of user3 should now be 1
        assertThat(waitingRoomService.getQueuePosition(showId, user3)).isEqualTo(1L);

        // Verify token validation and single use
        String token1 = admitted.get(0).token();
        boolean firstValidation = waitingRoomService.validateToken(token1, showId, user1);
        assertThat(firstValidation).isTrue();

        // Second use should fail (replay attack / reuse prevention)
        boolean secondValidation = waitingRoomService.validateToken(token1, showId, user1);
        assertThat(secondValidation).isFalse();

        // Token for different user or show should fail
        String token2 = admitted.get(1).token();
        boolean wrongUserValidation = waitingRoomService.validateToken(token2, showId, 9999L);
        assertThat(wrongUserValidation).isFalse();
    }

    @Test
    void expiredTokenFailsValidation() {
        Long showId = 888L;
        Long userId = 202L;

        // Negative duration -> immediately expired
        String expiredToken = tokenService.issueToken(showId, userId, 1L, Duration.ofSeconds(-10));
        boolean isValid = tokenService.validateAndConsumeToken(expiredToken, showId, userId);
        assertThat(isValid).isFalse();
    }
}
