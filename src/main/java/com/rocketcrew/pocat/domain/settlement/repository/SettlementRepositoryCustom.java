package com.rocketcrew.pocat.domain.settlement.repository;

import com.rocketcrew.pocat.domain.settlement.dto.request.AdminSettlementSearchCondition;
import com.rocketcrew.pocat.domain.settlement.dto.response.AdminSettlementResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SettlementRepositoryCustom {

    Page<AdminSettlementResponse> searchSettlements(AdminSettlementSearchCondition condition, Pageable pageable);
}
