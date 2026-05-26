package com.rocketcrew.pocat.domain.ai.prompt.repository;

import com.rocketcrew.pocat.domain.ai.prompt.entity.AiPromptTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AiPromptTemplateRepository extends JpaRepository<AiPromptTemplate, Long> {

    Optional<AiPromptTemplate> findByCardGradeAndIsActiveTrue(String cardGrade);

    Optional<AiPromptTemplate> findByIsActiveTrueAndCardGrade(String grade);
}
