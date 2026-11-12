package com.ticketrush.waitingroom;

import com.ticketrush.exception.GlobalExceptionHandler;
import com.ticketrush.ratelimit.QuotaMetrics;
import com.ticketrush.security.OwnershipService;
import com.ticketrush.show.ShowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WaitingRoomControllerUnitTest {

    @Mock WaitingRoomService waitingRoomService;
    @Mock ShowRepository showRepository;
    @Mock OwnershipService ownershipService;
    @Mock QuotaMetrics quotaMetrics;

    private MockMvc mvc;

    @BeforeEach
    void setup() {
        WaitingRoomController controller =
                new WaitingRoomController(waitingRoomService, showRepository, ownershipService);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(quotaMetrics))
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(MethodParameter parameter) {
                        Class<?> type = parameter.getParameterType();
                        return Jwt.class.isAssignableFrom(type) || Authentication.class.isAssignableFrom(type);
                    }

                    @Override
                    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                        return null;
                    }
                })
                .build();
    }

    @Test
    void joinPositionAdmitValidationAndValidateEndpointsWorkCleanly() throws Exception {
        when(showRepository.existsById(10L)).thenReturn(true);
        when(showRepository.existsById(99L)).thenReturn(false);
        when(ownershipService.requireUserId(any(), any())).thenReturn(42L);
        when(waitingRoomService.joinQueue(10L, 42L)).thenReturn(1L);
        when(waitingRoomService.getQueuePosition(10L, 42L)).thenReturn(1L);
        when(waitingRoomService.admitNextBatch(10L, 2))
                .thenReturn(List.of(new WaitingRoomService.AdmittedUser(42L, 1L, "tok-1")));
        when(waitingRoomService.validateToken("tok-1", 10L, 42L)).thenReturn(true);

        mvc.perform(post("/api/v1/shows/10/waiting-room/join"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.showId").value(10))
                .andExpect(jsonPath("$.userId").value(42))
                .andExpect(jsonPath("$.queueNumber").value(1));

        mvc.perform(get("/api/v1/shows/10/waiting-room/position"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inQueue").value(true))
                .andExpect(jsonPath("$.position").value(1));

        mvc.perform(post("/api/v1/shows/10/waiting-room/admit?count=0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));

        mvc.perform(post("/api/v1/shows/10/waiting-room/admit?count=501"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));

        mvc.perform(post("/api/v1/shows/10/waiting-room/admit?count=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(42))
                .andExpect(jsonPath("$[0].token").value("tok-1"));

        mvc.perform(post("/api/v1/shows/99/waiting-room/join"))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/v1/shows/10/waiting-room/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"tok-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));
    }
}
