package com.rocketcrew.pocat.domain.payment.scheduler;

import com.rocketcrew.pocat.domain.payment.entity.PaymentStatus;
import com.rocketcrew.pocat.domain.payment.entity.PaymentType;
import com.rocketcrew.pocat.domain.payment.repository.PaymentRepository;
import com.rocketcrew.pocat.domain.payment.service.PaymentCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 방치된 PENDING 결제를 주기적으로 FAILED 처리한다.
 *
 * PG_DIRECT: 구매자가 결제창을 열었지만 1시간 내 완료하지 않은 결제
 * BILLING_KEY: 서버 오류로 PortOne 호출 후 상태 처리가 실패한 결제 (30분 기준)
 *
 * 건별로 PaymentCleanupService에 위임하여 각각 독립 트랜잭션 + 비관적 락으로 처리한다.
 * 스케줄러 메서드 자체에 @Transactional을 두면 락 없이 여러 엔티티를 한 번에 플러시해
 * confirmPayment와 상태 불일치가 발생할 수 있으므로 이 구조를 유지한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PendingPaymentCleanupScheduler {

    private static final long DIRECT_EXPIRY_HOURS = 1;
    private static final long BILLING_KEY_EXPIRY_MINUTES = 30;

    private final PaymentRepository paymentRepository;
    private final PaymentCleanupService paymentCleanupService;

    @Scheduled(fixedDelay = 600_000)
    public void cleanupExpiredPendingPayments() {
        LocalDateTime now = LocalDateTime.now();

        List<String> directUids = paymentRepository.findExpiredPendingDirectPayments(
                        PaymentStatus.PENDING, PaymentType.PG_DIRECT, now.minusHours(DIRECT_EXPIRY_HOURS))
                .stream().map(p -> p.getPaymentUid()).toList();

        List<String> billingKeyUids = paymentRepository.findExpiredPendingDirectPayments(
                        PaymentStatus.PENDING, PaymentType.BILLING_KEY, now.minusMinutes(BILLING_KEY_EXPIRY_MINUTES))
                .stream().map(p -> p.getPaymentUid()).toList();

        int total = directUids.size() + billingKeyUids.size();
        if (total == 0) {
            log.debug("만료된 PENDING 결제 없음");
            return;
        }

        log.info("만료된 PENDING 결제 정리 시작 PG_DIRECT={} BILLING_KEY={}",
                directUids.size(), billingKeyUids.size());

        for (String uid : directUids) {
            try {
                paymentCleanupService.cleanupOne(uid);
            } catch (Exception e) {
                log.error("PENDING 결제 정리 실패 paymentUid={}", uid, e);
            }
        }
        for (String uid : billingKeyUids) {
            try {
                paymentCleanupService.cleanupOne(uid);
            } catch (Exception e) {
                log.error("PENDING 결제 정리 실패 paymentUid={}", uid, e);
            }
        }
    }
}
