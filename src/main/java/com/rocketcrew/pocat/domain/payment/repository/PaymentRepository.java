package com.rocketcrew.pocat.domain.payment.repository;

import com.rocketcrew.pocat.domain.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrderId(Long orderId);

    Optional<Payment> findByPaymentUid(String paymentUid);
}
