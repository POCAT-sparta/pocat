package com.rocketcrew.pocat.domain.payment.repository;

import com.rocketcrew.pocat.domain.payment.entity.Payment;
import com.rocketcrew.pocat.domain.payment.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByPaymentUid(String paymentUid);

    Optional<Payment> findByOrderId(Long orderId);

    Optional<Payment> findByOrderIdAndStatus(Long orderId, PaymentStatus status);

    boolean existsByOrderIdAndStatus(Long orderId, PaymentStatus status);
}
