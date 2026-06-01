package com.rocketcrew.pocat.domain.settlement.service;

import com.rocketcrew.pocat.domain.settlement.dto.request.AdminSettlementSearchCondition;
import com.rocketcrew.pocat.domain.settlement.dto.response.AdminSettlementResponse;
import com.rocketcrew.pocat.domain.settlement.enums.SettlementStatus;
import com.rocketcrew.pocat.domain.settlement.repository.SettlementRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminSettlementQueryServiceTest {

    @InjectMocks
    private AdminSettlementQueryService adminSettlementQueryService;

    @Mock
    private SettlementRepository settlementRepository;

    @Nested
    @DisplayName("getAdminSettlements()")
    class GetAdminSettlements {

        @Test
        @DisplayName("성공: 검색 조건과 페이지네이션으로 전체 정산 목록 반환")
        void success() {
            // given
            AdminSettlementSearchCondition condition = new AdminSettlementSearchCondition(
                    SettlementStatus.PENDING, null, null, null);
            Pageable pageable = PageRequest.of(0, 20);

            AdminSettlementResponse responseItem = new AdminSettlementResponse(
                    "SET-001", "ORD-001", "seller_nick",
                    "피카츄", "PSA_10", 10000L, 500L, 9500L,
                    SettlementStatus.PENDING, null, LocalDateTime.now()
            );
            Page<AdminSettlementResponse> page = new PageImpl<>(List.of(responseItem), pageable, 1);

            given(settlementRepository.searchSettlements(condition, pageable)).willReturn(page);

            // when
            Page<AdminSettlementResponse> result = adminSettlementQueryService.getAdminSettlements(condition, pageable);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).settlementUid()).isEqualTo("SET-001");
            assertThat(result.getContent().get(0).status()).isEqualTo(SettlementStatus.PENDING);
        }

        @Test
        @DisplayName("성공: 조건 없이 전체 정산 조회")
        void success_noCondition() {
            // given
            AdminSettlementSearchCondition condition = new AdminSettlementSearchCondition(
                    null, null, null, null);
            Pageable pageable = PageRequest.of(0, 20);
            Page<AdminSettlementResponse> emptyPage = new PageImpl<>(List.of(), pageable, 0);

            given(settlementRepository.searchSettlements(condition, pageable)).willReturn(emptyPage);

            // when
            Page<AdminSettlementResponse> result = adminSettlementQueryService.getAdminSettlements(condition, pageable);

            // then
            assertThat(result.getTotalElements()).isEqualTo(0);
            assertThat(result.getContent()).isEmpty();
        }
    }
}
