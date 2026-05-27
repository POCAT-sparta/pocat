package com.rocketcrew.pocat.domain.ai.analysis.repository;

import com.rocketcrew.pocat.domain.ai.analysis.entity.CardAiAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CardAiAnalysisRepository extends JpaRepository<CardAiAnalysis, Long> {

    Optional<CardAiAnalysis> findTopByCardIdOrderByCreatedAtDesc(Long cardId);
}
