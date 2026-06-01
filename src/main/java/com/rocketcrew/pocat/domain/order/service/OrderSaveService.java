package com.rocketcrew.pocat.domain.order.service;

import com.rocketcrew.pocat.domain.order.entity.Order;
import com.rocketcrew.pocat.domain.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderSaveService {

    private final OrderRepository orderRepository;

    @Transactional
    public Order saveBuyoutOrder(Long auctionId, Long cardId, Long sellerId, Long buyerId, Long finalPrice) {
        return orderRepository.save(Order.fromBuyout(auctionId, cardId, sellerId, buyerId, finalPrice));
    }
}
