package com.rocketcrew.pocat.domain.payment.service;

import com.rocketcrew.pocat.domain.order.entity.Order;
import com.rocketcrew.pocat.domain.order.enums.OrderStatus;
import com.rocketcrew.pocat.domain.order.service.OrderQueryService;
import com.rocketcrew.pocat.domain.payment.dto.response.PaymentResponse;
import com.rocketcrew.pocat.domain.payment.entity.Payment;
import com.rocketcrew.pocat.domain.payment.entity.PaymentStatus;
import com.rocketcrew.pocat.domain.payment.repository.PaymentRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.PaymentException;
import com.rocketcrew.pocat.support.TestFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PaymentQueryService")
class PaymentQueryServiceTest {

    @InjectMocks
    private PaymentQueryService paymentQueryService;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderQueryService orderQueryService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ── getPayment ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("getPayment()")
    class GetPayment {

        @Test
        @DisplayName("성공: 구매자 본인이 결제 정보를 조회한다")
        void success_asBuyer() {
            Payment payment = TestFixtures.aPayment(PaymentStatus.COMPLETED);
            Order order = TestFixtures.anOrder(OrderStatus.PAYMENT_COMPLETED); // buyerId=1L

            given(paymentRepository.findByPaymentUid("PAY-001")).willReturn(Optional.of(payment));
            given(orderQueryService.findByOrderid(1L)).willReturn(order);

            // SecurityContext: 일반 USER (ROLE_ADMIN 없음)
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("1", null,
                            List.of(new SimpleGrantedAuthority("ROLE_USER"))));

            PaymentResponse response = paymentQueryService.getPayment(1L, "PAY-001");

            assertThat(response).isNotNull();
            assertThat(response.paymentUid()).isEqualTo("PAY-001");
        }

        @Test
        @DisplayName("성공: ADMIN은 타인의 결제 정보도 조회한다")
        void success_asAdmin() {
            Payment payment = TestFixtures.aPayment(PaymentStatus.COMPLETED);
            Order order = TestFixtures.anOrder(OrderStatus.PAYMENT_COMPLETED); // buyerId=1L

            given(paymentRepository.findByPaymentUid("PAY-001")).willReturn(Optional.of(payment));
            given(orderQueryService.findByOrderid(1L)).willReturn(order);

            // SecurityContext: ADMIN
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("2", null,
                            List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

            // requesterId=99L (관리자이므로 buyerId 불일치 무관)
            PaymentResponse response = paymentQueryService.getPayment(99L, "PAY-001");

            assertThat(response).isNotNull();
            assertThat(response.paymentUid()).isEqualTo("PAY-001");
        }

        @Test
        @DisplayName("실패: 구매자 불일치 (비관리자) → PAYMENT_BUYER_MISMATCH")
        void fail_buyerMismatch() {
            Payment payment = TestFixtures.aPayment(PaymentStatus.COMPLETED);
            Order order = TestFixtures.anOrder(OrderStatus.PAYMENT_COMPLETED); // buyerId=1L

            given(paymentRepository.findByPaymentUid("PAY-001")).willReturn(Optional.of(payment));
            given(orderQueryService.findByOrderid(1L)).willReturn(order);

            // SecurityContext: 일반 USER
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("99", null,
                            List.of(new SimpleGrantedAuthority("ROLE_USER"))));

            assertThatThrownBy(() -> paymentQueryService.getPayment(99L, "PAY-001"))
                    .isInstanceOf(PaymentException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYMENT_BUYER_MISMATCH);
        }

        @Test
        @DisplayName("실패: 결제 없음 → PAYMENT_NOT_FOUND")
        void fail_paymentNotFound() {
            given(paymentRepository.findByPaymentUid("UNKNOWN")).willReturn(Optional.empty());

            assertThatThrownBy(() -> paymentQueryService.getPayment(1L, "UNKNOWN"))
                    .isInstanceOf(PaymentException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYMENT_NOT_FOUND);
        }
    }
}
