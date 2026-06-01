package com.rocketcrew.pocat.domain.payment.repository;

import com.rocketcrew.pocat.domain.payment.entity.Payment;
import com.rocketcrew.pocat.domain.payment.entity.PaymentStatus;
import com.rocketcrew.pocat.domain.payment.entity.PaymentType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.paymentUid = :paymentUid")
    Optional<Payment> findByPaymentUidWithLock(@Param("paymentUid") String paymentUid);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :id")
    Optional<Payment> findByIdWithLock(@Param("id") Long id);

    @Query("""
           SELECT p
           FROM Payment p
           WHERE p.paymentUid = :paymentUid
           """)
    Optional<Payment> findByPaymentUid(@Param("paymentUid") String paymentUid);

    Optional<Payment> findByOrderId(Long orderId);

    Optional<Payment> findByOrderIdAndPaymentType(Long orderId, PaymentType paymentType);

    Optional<Payment> findByOrderIdAndStatus(Long orderId, PaymentStatus status);

    boolean existsByOrderIdAndStatus(Long orderId, PaymentStatus status);

    /** PENDING 직접결제 중 생성 후 1시간이 지난 것 (결제창 미완료 방치 건) */
    @Query("SELECT p FROM Payment p WHERE p.status = :status AND p.paymentType = :type AND p.createdAt < :expiredBefore")
    List<Payment> findExpiredPendingDirectPayments(
            @Param("status") PaymentStatus status,
            @Param("type") PaymentType type,
            @Param("expiredBefore") LocalDateTime expiredBefore);
}
