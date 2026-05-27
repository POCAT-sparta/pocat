package com.rocketcrew.pocat.domain.order.service;

import com.rocketcrew.pocat.domain.order.dto.request.AdminOrderSearchCondition;
import com.rocketcrew.pocat.domain.order.dto.response.AdminOrderResponse;
import com.rocketcrew.pocat.domain.order.enums.DeliveryStatus;
import com.rocketcrew.pocat.domain.order.enums.OrderStatus;
import com.rocketcrew.pocat.domain.order.repository.OrderRepository;
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
@DisplayName("AdminOrderQueryService")
class AdminOrderQueryServiceTest {

    @InjectMocks
    private AdminOrderQueryService adminOrderQueryService;

    @Mock
    private OrderRepository orderRepository;

    // ── getAdminOrders ─────────────────────────────────────────────────

    @Nested
    @DisplayName("getAdminOrders()")
    class GetAdminOrders {

        @Test
        @DisplayName("성공: 조건과 페이지를 repository 에 위임하고 결과를 반환한다")
        void success() {
            AdminOrderSearchCondition condition = new AdminOrderSearchCondition(
                    OrderStatus.PAYMENT_COMPLETED,
                    DeliveryStatus.PREPARING,
                    null,
                    null,
                    null,
                    null,
                    null
            );
            Pageable pageable = PageRequest.of(0, 20);

            AdminOrderResponse sampleResponse = new AdminOrderResponse(
                    1L, "ORD-001", "구매자", "판매자", "피카츄", "PSA_10",
                    10000L, OrderStatus.PAYMENT_COMPLETED, DeliveryStatus.PREPARING,
                    LocalDateTime.now()
            );
            Page<AdminOrderResponse> expectedPage = new PageImpl<>(
                    List.of(sampleResponse), pageable, 1);

            given(orderRepository.searchOrders(condition, pageable)).willReturn(expectedPage);

            Page<AdminOrderResponse> result = adminOrderQueryService.getAdminOrders(condition, pageable);

            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).orderUid()).isEqualTo("ORD-001");
            assertThat(result.getTotalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("성공: 조건이 모두 null 이어도 결과를 반환한다 (전체 조회)")
        void success_emptyCondition() {
            AdminOrderSearchCondition condition = new AdminOrderSearchCondition(
                    null, null, null, null, null, null, null);
            Pageable pageable = PageRequest.of(0, 20);

            given(orderRepository.searchOrders(condition, pageable))
                    .willReturn(Page.empty(pageable));

            Page<AdminOrderResponse> result = adminOrderQueryService.getAdminOrders(condition, pageable);

            assertThat(result).isNotNull();
            assertThat(result.getContent()).isEmpty();
        }
    }
}
