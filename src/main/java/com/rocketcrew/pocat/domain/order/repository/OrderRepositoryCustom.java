package com.rocketcrew.pocat.domain.order.repository;

import com.rocketcrew.pocat.domain.order.dto.request.AdminOrderSearchCondition;
import com.rocketcrew.pocat.domain.order.dto.response.AdminOrderResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderRepositoryCustom {

    Page<AdminOrderResponse> searchOrders(AdminOrderSearchCondition condition, Pageable pageable);
}
