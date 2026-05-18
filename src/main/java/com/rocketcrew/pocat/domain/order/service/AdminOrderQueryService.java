package com.rocketcrew.pocat.domain.order.service;

import com.rocketcrew.pocat.domain.order.dto.request.AdminOrderSearchCondition;
import com.rocketcrew.pocat.domain.order.dto.response.AdminOrderResponse;
import com.rocketcrew.pocat.domain.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminOrderQueryService {

    private final OrderRepository orderRepository;

    public Page<AdminOrderResponse> getAdminOrders(AdminOrderSearchCondition condition, Pageable pageable) {
        return orderRepository.searchOrders(condition, pageable);
    }
}
