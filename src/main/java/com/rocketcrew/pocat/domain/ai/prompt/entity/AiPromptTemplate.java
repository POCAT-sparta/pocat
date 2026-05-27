package com.rocketcrew.pocat.domain.ai.prompt.entity;

import com.rocketcrew.pocat.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ai_prompt_template")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiPromptTemplate extends BaseEntity {

    @Column(nullable = false, length = 20)
    private String cardGrade;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String promptText;

    @Column(nullable = false)
    private Integer version;

    @Column(nullable = false)
    private Boolean isActive;
}
