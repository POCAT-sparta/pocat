package com.rocketcrew.pocat.domain.order.repository;

import com.rocketcrew.pocat.domain.order.entity.Order;
import com.rocketcrew.pocat.domain.order.enums.OrderStatus;
import com.rocketcrew.pocat.domain.order.repository.dto.AvgPriceAggregate;
import com.rocketcrew.pocat.global.config.JpaConfig;
import com.rocketcrew.pocat.global.config.QueryDslConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({JpaConfig.class, QueryDslConfig.class})
class OrderRepositoryTest {

    @Autowired
    private OrderRepository orderRepository;

    @Test
    @DisplayName("findAvgAndCountByCardId: PAYMENT_COMPLETED 주문 2건의 평균가와 건수를 반환한다")
    void findAvgAndCountByCardId_returnsAggregate() {
        Order order1 = Order.fromBuyout(null, 23190L, 1L, 2L, 80000L);
        order1.completePayment();
        Order order2 = Order.fromBuyout(null, 23190L, 1L, 3L, 85000L);
        order2.completePayment();
        orderRepository.save(order1);
        orderRepository.save(order2);
        orderRepository.flush();

        LocalDateTime since = LocalDateTime.now().minusMonths(6);
        AvgPriceAggregate result = orderRepository.findAvgAndCountByCardId(23190L, OrderStatus.PAYMENT_COMPLETED, since);

        assertThat(result).isNotNull();
        assertThat(result.transactionCount()).isEqualTo(2L);
        assertThat(result.averagePrice()).isEqualTo(82500.0);
    }
}
