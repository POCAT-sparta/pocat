package com.rocketcrew.pocat.global.filter;

import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.common.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.text.Normalizer;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("BadWordFilterService")
class BadWordFilterServiceTest {

    private BadWordFilterService badWordFilterService;

    @BeforeEach
    void setUp() {
        // 생성자에서 badwords.txt를 읽은 후 ReflectionTestUtils로 테스트 세트 교체
        badWordFilterService = new BadWordFilterService();
        ReflectionTestUtils.setField(badWordFilterService, "badWords", Set.of("욕설", "금지어", "비속어", "bad"));
    }

    @Nested
    @DisplayName("validate() — 단일 인자")
    class ValidateSingle {

        @Test
        @DisplayName("성공: null 입력 시 예외 없음")
        void success_nullInput() {
            assertThatCode(() -> badWordFilterService.validate((String) null))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("성공: blank 입력 시 예외 없음")
        void success_blankInput() {
            assertThatCode(() -> badWordFilterService.validate("   "))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("성공: 금지어 미포함 → 정상 통과")
        void success_noBadWord() {
            assertThatCode(() -> badWordFilterService.validate("안녕하세요 반갑습니다"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("실패: 금지어 포함 → ServiceException(CONTAINS_BAD_WORD)")
        void fail_containsBadWord() {
            assertThatThrownBy(() -> badWordFilterService.validate("이건 욕설 포함 텍스트"))
                    .isInstanceOf(ServiceException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONTAINS_BAD_WORD);
        }

        @Test
        @DisplayName("실패: NFD 분리된 금지어 → NFC 정규화 후 탐지")
        void fail_nfdDecomposedBadWord() {
            String nfdBadWord = Normalizer.normalize("욕설", Normalizer.Form.NFD);
            assertThatThrownBy(() -> badWordFilterService.validate(nfdBadWord))
                    .isInstanceOf(ServiceException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONTAINS_BAD_WORD);
        }

        @Test
        @DisplayName("실패: 대소문자 혼합 금지어 → Locale.ROOT toLowerCase 후 탐지")
        void fail_upperCaseBadWord() {
            assertThatThrownBy(() -> badWordFilterService.validate("BAD word here"))
                    .isInstanceOf(ServiceException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONTAINS_BAD_WORD);
        }
    }

    @Nested
    @DisplayName("validate() — 복수 인자")
    class ValidateMultiple {

        @Test
        @DisplayName("실패: 복수 인자 중 하나에 금지어 포함 → ServiceException(CONTAINS_BAD_WORD)")
        void fail_oneOfMultipleContainsBadWord() {
            assertThatThrownBy(() -> badWordFilterService.validate("정상 제목", "내용에 비속어 있음"))
                    .isInstanceOf(ServiceException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONTAINS_BAD_WORD);
        }

        @Test
        @DisplayName("성공: 복수 인자 모두 금지어 미포함 → 정상 통과")
        void success_noneContainsBadWord() {
            assertThatCode(() -> badWordFilterService.validate("정상 제목", "정상 내용"))
                    .doesNotThrowAnyException();
        }
    }
}
