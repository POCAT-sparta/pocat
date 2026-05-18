package com.rocketcrew.pocat.domain.refund.service;

import com.rocketcrew.pocat.domain.refund.dto.request.CreateRefundRequest;
import com.rocketcrew.pocat.domain.refund.dto.request.RejectRefundRequest;
import com.rocketcrew.pocat.domain.refund.dto.response.RefundResponse;
import com.rocketcrew.pocat.domain.refund.entity.Refund;
import com.rocketcrew.pocat.domain.refund.entity.RefundStatus;
import com.rocketcrew.pocat.domain.refund.repository.RefundRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.RefundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class RefundService {

    private final RefundRepository refundRepository;

    @Transactional(readOnly = true)
    public Page<RefundResponse> getRefunds(Pageable pageable) {
        return refundRepository.findAll(pageable)
                .map(RefundResponse::from);
    }

    @Transactional(readOnly = true)
    public RefundResponse getRefund(Long id) {
        Refund refund = refundRepository.findById(id)
                .orElseThrow(() -> new RefundException(ErrorCode.REFUND_NOT_FOUND));
        return RefundResponse.from(refund);
    }

    public RefundResponse createRefund(CreateRefundRequest request) {
        Refund refund = Refund.builder()
                .orderId(request.orderId())
                .paymentId(request.paymentId())
                .amount(request.amount())
                .reason(request.reason())
                .status(RefundStatus.REQUESTED)
                .build();
        Refund saved = refundRepository.save(refund);
        return RefundResponse.from(saved);
    }

    public RefundResponse approveRefund(Long id) {
        Refund refund = refundRepository.findById(id)
                .orElseThrow(() -> new RefundException(ErrorCode.REFUND_NOT_FOUND));
        refund.approve();
        return RefundResponse.from(refund);
    }

    public RefundResponse rejectRefund(Long id, RejectRefundRequest request) {
        Refund refund = refundRepository.findById(id)
                .orElseThrow(() -> new RefundException(ErrorCode.REFUND_NOT_FOUND));
        refund.reject(request.rejectReason());
        return RefundResponse.from(refund);
    }
}
