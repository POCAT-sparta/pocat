package com.rocketcrew.pocat.domain.ai.rag.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * RAG (Retrieval-Augmented Generation) 서비스.
 * 벡터 검색으로 관련 문서 검색 및 컨텍스트 구성.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    private final VectorStore vectorStore;

    private static final double SIMILARITY_THRESHOLD = 0.7;
    private static final int TOP_K = 5;

    /**
     * 사용자 질의와 유사한 문서 검색.
     *
     * @param query 검색 질의
     * @return 유사도 기준 상위 문서 목록
     */
    public List<Document> search(String query) {
        try {
            log.info("Searching RAG documents for query: {}", query);

            // VectorStore 검색 (유사도 기반)
            List<Document> results = vectorStore.similaritySearch(
                    SearchRequest.query(query)
                            .withTopK(TOP_K)
                            .withSimilarityThreshold(SIMILARITY_THRESHOLD)
            );

            log.debug("RAG search completed: found {} documents", results.size());
            return results;
        } catch (Exception e) {
            log.error("RAG search failed: {}", e.getMessage(), e);
            // 검색 실패 시 빈 리스트 반환 (fallback은 호출측 처리)
            return List.of();
        }
    }

    /**
     * 검색된 Document를 LLM 프롬프트용 컨텍스트로 변환.
     * 출처 정보(cardId, postId) 포함.
     *
     * @param docs 문서 목록
     * @return 포맷팅된 컨텍스트 문자열
     */
    public String buildContext(List<Document> docs) {
        if (docs.isEmpty()) {
            return "관련 문서를 찾을 수 없습니다.";
        }

        return docs.stream()
                .map(doc -> {
                    String type = doc.getMetadata().getOrDefault("type", "unknown");
                    String id = doc.getMetadata().getOrDefault(
                            type.equals("card") ? "cardId" : "postId",
                            "N/A"
                    );
                    return String.format("[%s #%s]: %s", type, id, doc.getContent());
                })
                .collect(Collectors.joining("\n\n"));
    }
}
