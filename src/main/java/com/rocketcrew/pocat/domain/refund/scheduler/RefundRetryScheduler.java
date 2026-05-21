package com.rocketcrew.pocat.domain.refund.scheduler;

import com.rocketcrew.pocat.domain.refund.entity.RefundStatus;
import com.rocketcrew.pocat.domain.refund.repository.RefundRepository;
import com.rocketcrew.pocat.domain.refund.service.RefundCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RefundRetryScheduler {

    private final RefundRepository refundRepository;
    private final RefundCommandService refundCommandService;

    @Scheduled(fixedDelay = 60_000)
    @Transactional(readOnly = true)
    public void retryFailedRefunds() {
        // ID만 추출해 별도 트랜잭션에서 처리 — 엔티티를 영속성 컨텍스트 밖에서
        // 직접 참조하면 LazyLoadingException 발생 위험이 있다.
        LocalDateTime now = LocalDateTime.now();
        // PROCESSING 상태가 5분 이상 지속되면 stuck으로 간주하여 재시도 대상에 포함
        LocalDateTime stuckBefore = now.minusMinutes(5);

        List<Long> ids = refundRepository
                .findRetryableTargets(RefundStatus.FAILED_RETRYABLE, now, RefundStatus.PROCESSING, stuckBefore)
                .stream()
                .map(r -> r.getId())
                .toList();

        if (ids.isEmpty()) {
            log.debug("환불 재시도 대상 없음");
            return;
        }

        log.info("환불 재시도 스케줄러 시작 count={}", ids.size());

        for (Long id : ids) {
            try {
                refundCommandService.retryRefund(id);
            } catch (Exception e) {
                log.error("환불 재시도 실패 refundId={}", id, e);
            }
        }
    }
}
