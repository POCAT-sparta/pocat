package com.rocketcrew.pocat.domain.ai.rag.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.rocketcrew.pocat.domain.ai.rag.service.AdminAiService;
import com.rocketcrew.pocat.global.dto.ApiResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "관리자 - AI RAG", description = "AI RAG 데이터 관리")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/ai")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAiController {

    private final AdminAiService adminAiService;

    /**
     * 활성 카드 전체를 벡터 스토어에 재색인한다.
     *
     * <p>작업은 비동기로 실행되며 즉시 202 Accepted를 반환한다.
     */
    @PostMapping("/reindex")
    public ResponseEntity<ApiResponseDto<Void>> reindex() {
        adminAiService.reindexAll();
        return ResponseEntity.accepted()
                .body(ApiResponseDto.success(HttpStatus.ACCEPTED, null));
    }
}
