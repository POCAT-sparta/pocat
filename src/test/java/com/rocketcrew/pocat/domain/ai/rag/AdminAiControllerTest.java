package com.rocketcrew.pocat.domain.ai.rag;

import com.rocketcrew.pocat.domain.ai.rag.controller.AdminAiController;
import com.rocketcrew.pocat.domain.ai.rag.service.AdminAiService;
import com.rocketcrew.pocat.global.exception.common.GlobalExceptionHandler;
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

import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AdminAiController 슬라이스 테스트.
 *
 * <p>[RED #168] AdminAiController / AdminAiService 가 아직 존재하지 않으므로
 * 컴파일 오류로 FAIL 상태입니다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminAiController")
class AdminAiControllerTest {

    private MockMvc mockMvc;

    @InjectMocks
    private AdminAiController adminAiController;

    @Mock
    private AdminAiService adminAiService;

    private CustomUserDetails adminDetails;

    @BeforeEach
    void setUp() {
        adminDetails = new TestCustomUserDetails(1L, "ADMIN");
        mockMvc = MockMvcBuilders.standaloneSetup(adminAiController)
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
                                return adminDetails;
                            }
                        })
                .build();
    }

    @Nested
    @DisplayName("POST /api/v1/admin/ai/reindex")
    class ReindexAll {

        @Test
        @DisplayName("[RED #168] ADMIN 권한으로 reindex 요청 시 202 Accepted 반환")
        void success_202_admin() throws Exception {
            // given
            doNothing().when(adminAiService).reindexAll();

            // when & then
            mockMvc.perform(post("/api/v1/admin/ai/reindex"))
                    .andExpect(status().isAccepted());
        }
    }
}
