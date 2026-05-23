package com.rocketcrew.pocat.domain.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rocketcrew.pocat.domain.user.dto.request.RegisterBillingKeyRequest;
import com.rocketcrew.pocat.domain.user.dto.request.UpdateBankRequest;
import com.rocketcrew.pocat.domain.user.dto.request.UpdateBillingKeyRequest;
import com.rocketcrew.pocat.domain.user.dto.request.UpdateUserRequest;
import com.rocketcrew.pocat.domain.user.dto.response.AdminUserResponse;
import com.rocketcrew.pocat.domain.user.dto.response.UserResponse;
import com.rocketcrew.pocat.domain.user.enums.UserRole;
import com.rocketcrew.pocat.domain.user.service.UserCommandService;
import com.rocketcrew.pocat.domain.user.service.UserQueryService;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.common.GlobalExceptionHandler;
import com.rocketcrew.pocat.global.exception.domain.UserException;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    private MockMvc mockMvc;

    @InjectMocks
    private UserController userController;

    @Mock
    private UserQueryService userQueryService;

    @Mock
    private UserCommandService userCommandService;

    private CustomUserDetails userDetails;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private UserResponse buildUserResponse() {
        return new UserResponse(
                1L, "test@example.com", "tester", "010-1234-5678",
                UserRole.USER, "국민은행", "123-456-789", "서울시",
                0, false, false, LocalDateTime.now()
        );
    }

    @BeforeEach
    void setUp() {
        userDetails = new TestCustomUserDetails(1L, "USER");
        mockMvc = MockMvcBuilders.standaloneSetup(userController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(
                        new PageableHandlerMethodArgumentResolver(),
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
    @DisplayName("GET /api/v1/users/me")
    class GetUserMe {

        @Test
        @DisplayName("성공: 내 정보 조회 → 200 OK")
        void success_200() throws Exception {
            // given
            given(userQueryService.getUserById(1L)).willReturn(buildUserResponse());

            // when & then
            mockMvc.perform(get("/api/v1/users/me"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("실패: 유저 없음 → 404 Not Found")
        void fail_404_userNotFound() throws Exception {
            // given
            given(userQueryService.getUserById(1L))
                    .willThrow(new UserException(ErrorCode.USER_NOT_FOUND));

            // when & then
            mockMvc.perform(get("/api/v1/users/me"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/users/me")
    class UpdateUser {

        @Test
        @DisplayName("성공: 프로필 업데이트 → 200 OK")
        void success_200() throws Exception {
            // given
            UpdateUserRequest request = new UpdateUserRequest("newNick", "010-9999-9999", "부산시");
            given(userCommandService.updateUser(eq(1L), any(UpdateUserRequest.class)))
                    .willReturn(buildUserResponse());

            // when & then
            mockMvc.perform(patch("/api/v1/users/me")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("실패: 유저 없음 → 404 Not Found")
        void fail_404_userNotFound() throws Exception {
            // given
            UpdateUserRequest request = new UpdateUserRequest("newNick", null, null);
            given(userCommandService.updateUser(eq(1L), any(UpdateUserRequest.class)))
                    .willThrow(new UserException(ErrorCode.USER_NOT_FOUND));

            // when & then
            mockMvc.perform(patch("/api/v1/users/me")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/users/me/billing-key")
    class RegisterBillingKey {

        @Test
        @DisplayName("성공: 빌링키 등록 → 200 OK")
        void success_200() throws Exception {
            // given
            RegisterBillingKeyRequest request = new RegisterBillingKeyRequest("billing-key-xyz");
            doNothing().when(userCommandService).registerBillingKey(eq(1L), any(RegisterBillingKeyRequest.class));

            // when & then
            mockMvc.perform(post("/api/v1/users/me/billing-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("실패: 이미 빌링키 존재 → 409 Conflict")
        void fail_409_billingKeyAlreadyExists() throws Exception {
            // given
            RegisterBillingKeyRequest request = new RegisterBillingKeyRequest("billing-key-xyz");
            doThrow(new UserException(ErrorCode.BILLING_KEY_ALREADY_EXISTS))
                    .when(userCommandService).registerBillingKey(eq(1L), any(RegisterBillingKeyRequest.class));

            // when & then
            mockMvc.perform(post("/api/v1/users/me/billing-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/users/me/billing-key")
    class DeleteBillingKey {

        @Test
        @DisplayName("성공: 빌링키 삭제 → 200 OK")
        void success_200() throws Exception {
            // given
            doNothing().when(userCommandService).deleteBillingKey(1L);

            // when & then
            mockMvc.perform(delete("/api/v1/users/me/billing-key"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("실패: 빌링키 없음 → 404 Not Found")
        void fail_404_billingKeyNotFound() throws Exception {
            // given
            doThrow(new UserException(ErrorCode.BILLING_KEY_NOT_FOUND))
                    .when(userCommandService).deleteBillingKey(1L);

            // when & then
            mockMvc.perform(delete("/api/v1/users/me/billing-key"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/users/me/billing-key")
    class UpdateBillingKey {

        @Test
        @DisplayName("성공: 빌링키 업데이트 → 200 OK")
        void success_200() throws Exception {
            // given
            UpdateBillingKeyRequest request = new UpdateBillingKeyRequest("new-billing-key");
            doNothing().when(userCommandService).updateBillingKey(eq(1L), any(UpdateBillingKeyRequest.class));

            // when & then
            mockMvc.perform(put("/api/v1/users/me/billing-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("실패: 빌링키 없음 → 404 Not Found")
        void fail_404_billingKeyNotFound() throws Exception {
            // given
            UpdateBillingKeyRequest request = new UpdateBillingKeyRequest("new-billing-key");
            doThrow(new UserException(ErrorCode.BILLING_KEY_NOT_FOUND))
                    .when(userCommandService).updateBillingKey(eq(1L), any(UpdateBillingKeyRequest.class));

            // when & then
            mockMvc.perform(put("/api/v1/users/me/billing-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/users/me/bank-account")
    class UpdateBank {

        @Test
        @DisplayName("성공: 계좌 정보 업데이트 → 200 OK")
        void success_200() throws Exception {
            // given
            UpdateBankRequest request = new UpdateBankRequest("국민은행", "123-456-789");
            doNothing().when(userCommandService).updateBank(eq(1L), any(UpdateBankRequest.class));

            // when & then
            mockMvc.perform(put("/api/v1/users/me/bank-account")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/admin/users")
    class GetAllUsers {

        @Test
        @DisplayName("성공: 유저 목록 조회 → 200 OK")
        void success_200() throws Exception {
            // given
            AdminUserResponse adminResponse = new AdminUserResponse(
                    1L, "test@example.com", "tester", "***-****-5678",
                    UserRole.USER, "국민은행", "***-789", "서울시",
                    0, false, false, LocalDateTime.now()
            );
            Page<AdminUserResponse> page = new PageImpl<>(List.of(adminResponse));
            given(userQueryService.getAllUsers(any(), any(), any(Pageable.class))).willReturn(page);

            // when & then
            mockMvc.perform(get("/api/v1/admin/users"))
                    .andExpect(status().isOk());
        }
    }
}
