package com.rocketcrew.pocat.domain.settlement.service;

import com.rocketcrew.pocat.domain.order.entity.Order;
import com.rocketcrew.pocat.domain.order.repository.OrderRepository;
import com.rocketcrew.pocat.domain.settlement.entity.Settlement;
import com.rocketcrew.pocat.domain.settlement.enums.SettlementStatus;
import com.rocketcrew.pocat.domain.settlement.repository.SettlementRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.OrderException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class SettlementCommandService {
    // TODO: 추후에 이벤트 처리로 고도화 가능 : 결제 완료 이벤트 발행 -> 리스너가 컨슘 -> 정산 객체 생성

    // 수수료 비율 미정 - 추후 변경 시 이 상수만 수정
    private static final double PLATFORM_FEE_RATE = 0.10;

    private final SettlementRepository settlementRepository;
    private final OrderRepository orderRepository;

    public void createSettlement(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderException(ErrorCode.ORDER_NOT_FOUND));

        long totalPrice = order.getFinalPrice();
        long platformFee = Math.round(totalPrice * PLATFORM_FEE_RATE);
        long sellerAmount = totalPrice - platformFee;

        Settlement settlement = Settlement.builder()
                .settlementUid(UUID.randomUUID().toString())
                .orderId(orderId)
                .sellerId(order.getSellerId())
                .totalPrice(totalPrice)
                .platformFee(platformFee)
                .sellerAmount(sellerAmount)
                .status(SettlementStatus.PENDING)
                .build();

        settlementRepository.save(settlement);
    }
}
