package com.rocketcrew.pocat.internal.testscenario.order.service;

import com.rocketcrew.pocat.domain.order.entity.Order;
import com.rocketcrew.pocat.domain.order.enums.OrderStatus;
import com.rocketcrew.pocat.domain.order.repository.OrderRepository;
import com.rocketcrew.pocat.domain.order.service.EscalationResult;
import com.rocketcrew.pocat.domain.order.service.OrderCommandService;
import com.rocketcrew.pocat.domain.order.service.SetExpireService;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.OrderException;
import com.rocketcrew.pocat.internal.testscenario.order.dto.OrderPaymentDeadlineInjectionResponse;
import com.rocketcrew.pocat.internal.testscenario.order.dto.OrderPaymentDeadlineScheduleResponse;
import com.rocketcrew.pocat.internal.testscenario.support.TestScenarioGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderTestScenarioService {

    private final OrderRepository orderRepository;
    private final OrderCommandService orderCommandService;
    private final SetExpireService setExpireService;
    private final TestScenarioGuard testScenarioGuard;

    public OrderPaymentDeadlineInjectionResponse expirePaymentWindowNow(String orderUid) {
        testScenarioGuard.ensureEnabled();

        Order order = findAutoPaymentFailedOrder(orderUid);
        EscalationResult result = orderCommandService.escalateToNextRankWithDirectPayment(orderUid);

        log.info("[TEST_SCENARIO] 직접결제 기한 즉시 만료 주입 orderUid={} result={}",
                orderUid, result.status());

        return new OrderPaymentDeadlineInjectionResponse(
                order.getOrderUid(),
                order.getId(),
                order.getStatus(),
                result.status(),
                result.nextBidderId(),
                result.nextOrderUid(),
                true,
                verificationHint(result)
        );
    }

    @Transactional
    public OrderPaymentDeadlineScheduleResponse schedulePaymentWindowExpiration(String orderUid, long ttlSeconds) {
        testScenarioGuard.ensureEnabled();
        if (ttlSeconds < 10 || ttlSeconds > 3600) {
            throw new OrderException(ErrorCode.INVALID_INPUT);
        }

        Order order = findAutoPaymentFailedOrder(orderUid);
        LocalDateTime beforePaymentDeadline = order.getPaymentDeadline();
        LocalDateTime afterPaymentDeadline = LocalDateTime.now().plusSeconds(ttlSeconds);

        int updated = orderRepository.updatePaymentDeadlineByOrderUidAndStatus(
                orderUid,
                OrderStatus.AUTO_PAYMENT_FAILED,
                afterPaymentDeadline
        );
        if (updated != 1) {
            throw new OrderException(ErrorCode.ORDER_CANNOT_FAIL_PAYMENT);
        }

        setExpireService.cancelExpiry(order.getId());
        setExpireService.scheduleExpiry(order.getId(), Duration.ofSeconds(ttlSeconds));

        log.info("[TEST_SCENARIO] 직접결제 Redis 만료 예약 주입 orderUid={} beforeDeadline={} afterDeadline={} ttlSeconds={}",
                orderUid, beforePaymentDeadline, afterPaymentDeadline, ttlSeconds);

        return new OrderPaymentDeadlineScheduleResponse(
                order.getOrderUid(),
                order.getId(),
                OrderStatus.AUTO_PAYMENT_FAILED,
                beforePaymentDeadline,
                afterPaymentDeadline,
                ttlSeconds,
                true,
                "Redis key expiration should trigger ExpiryEventListener and escalate to the next bidder or cancel the auction."
        );
    }

    private Order findAutoPaymentFailedOrder(String orderUid) {
        Order order = orderRepository.findByOrderUid(orderUid)
                .orElseThrow(() -> new OrderException(ErrorCode.ORDER_NOT_FOUND));
        if (order.getStatus() != OrderStatus.AUTO_PAYMENT_FAILED) {
            throw new OrderException(ErrorCode.ORDER_CANNOT_FAIL_PAYMENT);
        }
        return order;
    }

    private String verificationHint(EscalationResult result) {
        return switch (result.status()) {
            case ESCALATED -> "Check the new orderUid and confirm it is AUTO_PAYMENT_FAILED with a fresh direct-payment deadline.";
            case CANCELLED -> "Check the auction status. It should be CANCELLED because there is no eligible next bidder.";
            case SKIPPED -> "No state transition was applied. Check the source order status and bidder rank.";
        };
    }
}
