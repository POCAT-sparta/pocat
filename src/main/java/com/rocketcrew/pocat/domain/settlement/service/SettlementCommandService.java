package com.rocketcrew.pocat.domain.settlement.service;

import com.rocketcrew.pocat.domain.order.entity.Order;
import com.rocketcrew.pocat.domain.order.repository.OrderRepository;
import com.rocketcrew.pocat.domain.settlement.entity.Settlement;
import com.rocketcrew.pocat.domain.settlement.enums.SettlementStatus;
import com.rocketcrew.pocat.domain.settlement.event.SettlementCreatedEvent;
import com.rocketcrew.pocat.domain.settlement.repository.SettlementRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.SettlementException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import com.rocketcrew.pocat.global.util.PlatformFeePolicy;
import com.rocketcrew.pocat.global.util.TsidGenerator;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SettlementCommandService {

    private final SettlementRepository settlementRepository;
    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;

    public void createSettlement(String orderUid) {
        Order order = orderRepository.findByOrderUid(orderUid)
                .orElseThrow(() -> new SettlementException(ErrorCode.ORDER_NOT_FOUND));

        if (settlementRepository.existsByOrderId(order.getId())) {
            return;
        }

        long totalPrice = order.getFinalPrice();
        long platformFee = BigDecimal.valueOf(totalPrice)
                .multiply(BigDecimal.valueOf(PlatformFeePolicy.RATE))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                .longValue();
        long sellerAmount = totalPrice - platformFee;

        Settlement settlement = Settlement.builder()
                .settlementUid(TsidGenerator.generateSettlementUid())
                .orderId(order.getId())
                .sellerId(order.getSellerId())
                .totalPrice(totalPrice)
                .platformFee(platformFee)
                .sellerAmount(sellerAmount)
                .status(SettlementStatus.PENDING)
                .build();

        try {
            settlementRepository.saveAndFlush(settlement);
        } catch (DataIntegrityViolationException e) {
            // orderId 중복: 동시 요청 레이스 컨디션으로 다른 스레드가 먼저 정산을 생성한 경우
            if (settlementRepository.existsByOrderId(order.getId())) {
                log.warn("정산 생성 중복 예외 — 이미 존재하여 무시 orderId={}", order.getId());
                return;
            }
            // orderId 중복이 아닌 다른 무결성 오류(settlementUid 충돌 등) — 정산 누락 방지를 위해 전파
            throw e;
        }

        // 정산 생성 이벤트 발행
        eventPublisher.publishEvent(new SettlementCreatedEvent(
                settlement.getSettlementUid(),
                settlement.getSellerId(),
                settlement.getSellerAmount()
        ));
    }
}
