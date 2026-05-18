package com.rocketcrew.pocat.domain.settlement.service;

import com.rocketcrew.pocat.domain.settlement.dto.response.SettlementResponse;
import com.rocketcrew.pocat.domain.settlement.entity.Settlement;
import com.rocketcrew.pocat.domain.settlement.repository.SettlementRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.SettlementException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class SettlementService {

    private final SettlementRepository settlementRepository;

    @Transactional(readOnly = true)
    public Page<SettlementResponse> getSettlements(Long sellerId, Pageable pageable) {
        return settlementRepository.findBySellerId(sellerId, pageable)
                .map(SettlementResponse::from);
    }

    @Transactional(readOnly = true)
    public SettlementResponse getSettlement(Long id) {
        Settlement settlement = settlementRepository.findById(id)
                .orElseThrow(() -> new SettlementException(ErrorCode.SETTLEMENT_NOT_FOUND));
        return SettlementResponse.from(settlement);
    }
}
