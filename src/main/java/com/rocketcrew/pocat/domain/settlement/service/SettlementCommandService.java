package com.rocketcrew.pocat.domain.settlement.service;

import com.rocketcrew.pocat.domain.order.entity.Order;
import com.rocketcrew.pocat.domain.order.repository.OrderRepository;
import com.rocketcrew.pocat.domain.settlement.entity.Settlement;
import com.rocketcrew.pocat.domain.settlement.enums.SettlementStatus;
import com.rocketcrew.pocat.domain.settlement.repository.SettlementRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.OrderException;
import com.rocketcrew.pocat.global.exception.domain.SettlementException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import com.rocketcrew.pocat.global.util.PlatformFeePolicy;
import com.rocketcrew.pocat.global.util.TsidGenerator;

@Service
@RequiredArgsConstructor
@Transactional
public class SettlementCommandService {
    // TODO: 추후에 이벤트 처리로 고도화 가능 : 결제 완료 이벤트 발행 -> 리스너가 컨슘 -> 정산 객체 생성


    private final SettlementRepository settlementRepository;
    private final OrderRepository orderRepository;

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
            settlementRepository.save(settlement);
        } catch (DataIntegrityViolationException e) {
            // 동시 요청 레이스 컨디션 - 다른 스레드가 이미 생성한 것으로 간주
        }
    }
}
