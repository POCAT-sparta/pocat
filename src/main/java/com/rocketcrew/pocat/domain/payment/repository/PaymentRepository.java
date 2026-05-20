package com.rocketcrew.pocat.domain.payment.repository;

import com.rocketcrew.pocat.domain.payment.entity.Payment;
import com.rocketcrew.pocat.domain.payment.entity.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
           SELECT p
           FROM Payment p
           WHERE p.paymentUid = :paymentUid
           """)
    Optional<Payment> findByPaymentUidWithLock(@Param("paymentUid") String paymentUid);

    @Query("""
           SELECT p
           FROM Payment p
           WHERE p.paymentUid = :paymentUid
           """)
    Optional<Payment> findByPaymentUid(@Param("paymentUid") String paymentUid);

    Optional<Payment> findByOrderId(Long orderId);

    Optional<Payment> findByOrderIdAndStatus(Long orderId, PaymentStatus status);

    boolean existsByOrderIdAndStatus(Long orderId, PaymentStatus status);
}
