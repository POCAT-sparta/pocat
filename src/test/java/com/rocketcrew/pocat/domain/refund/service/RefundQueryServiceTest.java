package com.rocketcrew.pocat.domain.refund.service;

import com.rocketcrew.pocat.domain.order.entity.Order;
import com.rocketcrew.pocat.domain.order.enums.OrderStatus;
import com.rocketcrew.pocat.domain.order.repository.OrderRepository;
import com.rocketcrew.pocat.domain.refund.dto.response.AdminRefundResponse;
import com.rocketcrew.pocat.domain.refund.dto.response.RefundResponse;
import com.rocketcrew.pocat.domain.refund.entity.Refund;
import com.rocketcrew.pocat.domain.refund.entity.RefundStatus;
import com.rocketcrew.pocat.domain.refund.repository.RefundRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.RefundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RefundQueryServiceTest {

    @InjectMocks
    private RefundQueryService refundQueryService;

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private OrderRepository orderRepository;

    private final Long buyerId = 3L;
    private final Long orderId = 100L;
    private final Long refundId = 1L;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private Refund buildRefund(RefundStatus status) {
        Refund refund = Refund.builder()
                .orderId(orderId)
                .paymentId(200L)
                .amount(10000L)
                .reason("단순 변심")
                .status(status)
                .build();
        ReflectionTestUtils.setField(refund, "id", refundId);
        ReflectionTestUtils.setField(refund, "createdAt", LocalDateTime.now());
        ReflectionTestUtils.setField(refund, "updatedAt", LocalDateTime.now());
        return refund;
    }

    private Order buildOrder() {
        Order order = Order.builder()
                .cardId(10L)
                .sellerId(2L)
                .buyerId(buyerId)
                .orderUid("ORD-001")
                .finalPrice(10000L)
                .status(OrderStatus.PAYMENT_COMPLETED)
                .build();
        ReflectionTestUtils.setField(order, "id", orderId);
        return order;
    }

    @Nested
    @DisplayName("getMyRefunds()")
    class GetMyRefunds {

        @Test
        @DisplayName("성공: status 필터로 내 환불 목록 조회")
        void success_withStatusFilter() {
            // given
            Pageable pageable = PageRequest.of(0, 10);
            RefundResponse item = new RefundResponse(refundId, orderId, 200L, 10000L,
                    "단순 변심", null, RefundStatus.REQUESTED, LocalDateTime.now(), LocalDateTime.now());
            Page<RefundResponse> page = new PageImpl<>(List.of(item), pageable, 1);

            given(refundRepository.findMyRefunds(buyerId, RefundStatus.REQUESTED, pageable)).willReturn(page);

            // when
            Page<RefundResponse> result = refundQueryService.getMyRefunds(buyerId, RefundStatus.REQUESTED, pageable);

            // then
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).status()).isEqualTo(RefundStatus.REQUESTED);
        }

        @Test
        @DisplayName("성공: status 없이 전체 내 환불 목록 조회")
        void success_withoutFilter() {
            // given
            Pageable pageable = PageRequest.of(0, 10);
            RefundResponse item = new RefundResponse(refundId, orderId, 200L, 10000L,
                    "단순 변심", null, RefundStatus.REQUESTED, LocalDateTime.now(), LocalDateTime.now());
            Page<RefundResponse> page = new PageImpl<>(List.of(item), pageable, 1);

            given(refundRepository.findMyRefunds(buyerId, null, pageable)).willReturn(page);

            // when
            Page<RefundResponse> result = refundQueryService.getMyRefunds(buyerId, null, pageable);

            // then
            assertThat(result.getTotalElements()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("getRefund()")
    class GetRefund {

        @Test
        @DisplayName("성공: 구매자 본인이 조회")
        void success_buyer() {
            // given
            Refund refund = buildRefund(RefundStatus.REQUESTED);
            Order order = buildOrder();

            given(refundRepository.findById(refundId)).willReturn(Optional.of(refund));
            given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

            // when
            RefundResponse response = refundQueryService.getRefund(buyerId, refundId);

            // then
            assertThat(response).isNotNull();
            assertThat(response.refundId()).isEqualTo(refundId);
            assertThat(response.orderId()).isEqualTo(orderId);
        }

        @Test
        @DisplayName("성공: ADMIN이 조회")
        void success_admin() {
            // given
            // Set ADMIN in SecurityContext
            UsernamePasswordAuthenticationToken adminAuth = new UsernamePasswordAuthenticationToken(
                    "admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
            SecurityContextHolder.getContext().setAuthentication(adminAuth);

            Refund refund = buildRefund(RefundStatus.REQUESTED);
            Order order = buildOrder(); // buyerId = 3L, but requester is admin (different id)

            given(refundRepository.findById(refundId)).willReturn(Optional.of(refund));
            given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

            // when — admin passes different requesterId (e.g. 999L) but should still succeed
            RefundResponse response = refundQueryService.getRefund(999L, refundId);

            // then
            assertThat(response).isNotNull();
            assertThat(response.refundId()).isEqualTo(refundId);
        }

        @Test
        @DisplayName("실패: 구매자 불일치")
        void fail_buyerMismatch() {
            // given — no admin authority in context
            SecurityContextHolder.clearContext();

            Refund refund = buildRefund(RefundStatus.REQUESTED);
            Order order = buildOrder(); // buyerId = 3L

            given(refundRepository.findById(refundId)).willReturn(Optional.of(refund));
            given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

            // when & then — requester is 999L, not the buyer
            assertThatThrownBy(() -> refundQueryService.getRefund(999L, refundId))
                    .isInstanceOf(RefundException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REFUND_BUYER_MISMATCH);
        }

        @Test
        @DisplayName("실패: 환불 없음")
        void fail_refundNotFound() {
            // given
            given(refundRepository.findById(refundId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> refundQueryService.getRefund(buyerId, refundId))
                    .isInstanceOf(RefundException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REFUND_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("getAdminRefunds()")
    class GetAdminRefunds {

        @Test
        @DisplayName("성공: 관리자 전체 환불 목록 조회")
        void success() {
            // given
            Pageable pageable = PageRequest.of(0, 20);
            AdminRefundResponse item = new AdminRefundResponse(refundId, orderId, "buyer_nick",
                    10000L, "단순 변심", RefundStatus.REQUESTED, LocalDateTime.now(), LocalDateTime.now());
            Page<AdminRefundResponse> page = new PageImpl<>(List.of(item), pageable, 1);

            given(refundRepository.findAdminRefunds(RefundStatus.REQUESTED, pageable)).willReturn(page);

            // when
            Page<AdminRefundResponse> result = refundQueryService.getAdminRefunds(RefundStatus.REQUESTED, pageable);

            // then
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).buyerNickname()).isEqualTo("buyer_nick");
        }
    }
}
