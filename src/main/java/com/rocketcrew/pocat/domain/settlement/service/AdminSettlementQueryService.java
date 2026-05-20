package com.rocketcrew.pocat.domain.settlement.service;

import com.rocketcrew.pocat.domain.settlement.dto.request.AdminSettlementSearchCondition;
import com.rocketcrew.pocat.domain.settlement.dto.response.AdminSettlementResponse;
import com.rocketcrew.pocat.domain.settlement.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminSettlementQueryService {

    private final SettlementRepository settlementRepository;

    public Page<AdminSettlementResponse> getAdminSettlements(AdminSettlementSearchCondition condition, Pageable pageable) {
        return settlementRepository.searchSettlements(condition, pageable);
    }
}
