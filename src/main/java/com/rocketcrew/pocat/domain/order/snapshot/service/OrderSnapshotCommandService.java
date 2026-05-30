package com.rocketcrew.pocat.domain.order.snapshot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcrew.pocat.domain.order.entity.Order;
import com.rocketcrew.pocat.domain.order.repository.OrderRepository;
import com.rocketcrew.pocat.domain.order.snapshot.entity.OrderSnapshot;
import com.rocketcrew.pocat.domain.order.snapshot.repository.OrderSnapshotRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.OrderException;
import com.rocketcrew.pocat.global.util.PlatformFeePolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class OrderSnapshotCommandService {

    private final OrderSnapshotRepository orderSnapshotRepository;
    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;

    public void createSnapshot(String orderUid) {
        Order order = orderRepository.findByOrderUid(orderUid)
                .orElseThrow(() -> new OrderException(ErrorCode.ORDER_NOT_FOUND));

        if (orderSnapshotRepository.findByOrderUid(orderUid).isPresent()) {
            return;
        }

        long fee = BigDecimal.valueOf(order.getFinalPrice())
                .multiply(BigDecimal.valueOf(PlatformFeePolicy.RATE))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                .longValue();
        long sellerAmount = order.getFinalPrice() - fee;

        OrderSnapshot snapshot = OrderSnapshot.builder()
                .orderUid(order.getOrderUid())
                .finalPrice(order.getFinalPrice())
                .feeRate(PlatformFeePolicy.RATE)
                .fee(fee)
                .sellerAmount(sellerAmount)
                .snapshotJson(buildSnapshotJson(order))
                .build();

        try {
            orderSnapshotRepository.saveAndFlush(snapshot);
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
