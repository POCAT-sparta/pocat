package com.rocketcrew.pocat.domain.notification.controller;

import com.rocketcrew.pocat.domain.notification.dto.response.NotificationListResponse;
import com.rocketcrew.pocat.domain.notification.dto.response.NotificationResponse;
import com.rocketcrew.pocat.domain.notification.service.NotificationCommandService;
import com.rocketcrew.pocat.domain.notification.service.NotificationQueryService;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.common.GlobalExceptionHandler;
import com.rocketcrew.pocat.global.exception.domain.NotificationException;
import com.rocketcrew.pocat.global.security.CustomUserDetails;
import com.rocketcrew.pocat.support.TestCustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationController")
class NotificationControllerTest {

    private MockMvc mockMvc;

    @InjectMocks
    private NotificationController notificationController;

    @Mock
    private NotificationCommandService notificationCommandService;

    @Mock
    private NotificationQueryService notificationQueryService;

    private CustomUserDetails userDetails;

    private NotificationResponse sampleResponse() {
        return new NotificationResponse(10L, "BID_OUTBID", "입찰이 초과되었습니다.", false, null, LocalDateTime.now());
    }

    @BeforeEach
    void setUp() {
        userDetails = new TestCustomUserDetails(1L, "USER");
        mockMvc = MockMvcBuilders.standaloneSetup(notificationController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(
                        new HandlerMethodArgumentResolver() {
                            @Override
                            public boolean supportsParameter(MethodParameter parameter) {
                                return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
                            }

                            @Override
                            public Object resolveArgument(MethodParameter parameter,
                                                          ModelAndViewContainer mavContainer,
                                                          NativeWebRequest webRequest,
                                                          WebDataBinderFactory binderFactory) {
                                return userDetails;
                            }
                        })
                .build();
    }

    @Nested
    @DisplayName("GET /api/notifications")
    class GetNotifications {

        @Test
        @DisplayName("200 성공: 알림 목록 조회")
        void success() throws Exception {
            NotificationListResponse listResponse = new NotificationListResponse(
                    List.of(sampleResponse()), null, false);
            given(notificationQueryService.getNotifications(eq(1L), any()))
                    .willReturn(listResponse);

            mockMvc.perform(get("/api/notifications"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content[0].notificationId").value(10L))
                    .andExpect(jsonPath("$.data.content[0].type").value("BID_OUTBID"));
        }
    }

    @Nested
    @DisplayName("PUT /api/notifications/{notificationId}/read")
    class ReadNotification {

        @Test
        @DisplayName("200 성공: 알림 읽음 처리")
        void success() throws Exception {
            given(notificationCommandService.read(1L, 10L)).willReturn(sampleResponse());

            mockMvc.perform(put("/api/notifications/10/read"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.notificationId").value(10L))
                    .andExpect(jsonPath("$.data.type").value("BID_OUTBID"));
        }

        @Test
        @DisplayName("404 실패: 알림 없음")
        void notFound() throws Exception {
            given(notificationCommandService.read(1L, 999L))
                    .willThrow(new NotificationException(ErrorCode.NOTIFICATION_NOT_FOUND));

            mockMvc.perform(put("/api/notifications/999/read"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
        }

        @Test
        @DisplayName("403 실패: 접근 권한 없음")
        void accessDenied() throws Exception {
            given(notificationCommandService.read(1L, 10L))
                    .willThrow(new NotificationException(ErrorCode.NOTIFICATION_ACCESS_DENIED));

            mockMvc.perform(put("/api/notifications/10/read"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("NOTIFICATION_ACCESS_DENIED"));
        }
    }

    @Nested
    @DisplayName("PUT /api/notifications/read")
    class ReadAllNotifications {

        @Test
        @DisplayName("200 성공: 전체 읽음 처리")
        void success() throws Exception {
            willDoNothing().given(notificationCommandService).readAll(1L);

            mockMvc.perform(put("/api/notifications/read"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    @Nested
    @DisplayName("DELETE /api/notifications/{notificationId}")
    class DeleteNotification {

        @Test
        @DisplayName("200 성공: 알림 삭제")
        void success() throws Exception {
            willDoNothing().given(notificationCommandService).delete(1L, 10L);

            mockMvc.perform(delete("/api/notifications/10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("404 실패: 알림 없음")
        void notFound() throws Exception {
            willThrow(new NotificationException(ErrorCode.NOTIFICATION_NOT_FOUND))
                    .given(notificationCommandService).delete(1L, 999L);

            mockMvc.perform(delete("/api/notifications/999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("DELETE /api/notifications")
    class DeleteAllNotifications {

        @Test
        @DisplayName("200 성공: 전체 알림 삭제")
        void success() throws Exception {
            willDoNothing().given(notificationCommandService).deleteAll(1L);

            mockMvc.perform(delete("/api/notifications"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }
}
