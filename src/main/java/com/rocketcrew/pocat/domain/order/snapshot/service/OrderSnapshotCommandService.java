package com.rocketcrew.pocat.domain.order.snapshot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcrew.pocat.domain.order.entity.Order;
import com.rocketcrew.pocat.domain.order.repository.OrderRepository;
import com.rocketcrew.pocat.domain.order.snapshot.entity.OrderSnapshot;
import com.rocketcrew.pocat.domain.order.snapshot.repository.OrderSnapshotRepository;
import com.rocketcrew.pocat.domain.settlement.entity.Settlement;
import com.rocketcrew.pocat.domain.settlement.repository.SettlementRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.OrderException;
import com.rocketcrew.pocat.global.exception.domain.SettlementException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class OrderSnapshotCommandService {

    private static final long PLATFORM_FEE_RATE = 5L;

    private final OrderSnapshotRepository orderSnapshotRepository;
    private final OrderRepository orderRepository;
    private final SettlementRepository settlementRepository;
    private final ObjectMapper objectMapper;

    public void createSnapshot(String orderUid) {
        Order order = orderRepository.findByOrderUid(orderUid)
                .orElseThrow(() -> new OrderException(ErrorCode.ORDER_NOT_FOUND));

        if (orderSnapshotRepository.findByOrderUid(orderUid).isPresent()) {
            return;
        }

        Settlement settlement = settlementRepository.findByOrderId(order.getId())
                .orElseThrow(() -> new OrderException(ErrorCode.SETTLEMENT_NOT_FOUND));

        OrderSnapshot snapshot = OrderSnapshot.builder()
                .orderUid(order.getOrderUid())
                .finalPrice(order.getFinalPrice())
                .feeRate(PLATFORM_FEE_RATE)
                .fee(settlement.getPlatformFee())
                .sellerAmount(settlement.getSellerAmount())
                .snapshotJson(buildSnapshotJson(order))
                .build();

        try {
            orderSnapshotRepository.save(snapshot);
        } catch (DataIntegrityViolationException e) {
            log.debug("스냅샷 동시 생성 감지 - orderUid: {}, 기존 스냅샷으로 처리", orderUid);
        }
    }

    private String buildSnapshotJson(Order order) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderUid", order.getOrderUid());
        data.put("auctionId", order.getAuctionId());
        data.put("cardId", order.getCardId());
        data.put("sellerId", order.getSellerId());
        data.put("buyerId", order.getBuyerId());
        data.put("finalPrice", order.getFinalPrice());
        data.put("status", order.getStatus().name());
        data.put("deliveryStatus", order.getDeliveryStatus() != null ? order.getDeliveryStatus().name() : null);
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            log.error("스냅샷 JSON 직렬화 실패 - orderUid: {}", order.getOrderUid(), e);
            throw new IllegalStateException("스냅샷 JSON 직렬화 실패", e);
        }
    }
}
