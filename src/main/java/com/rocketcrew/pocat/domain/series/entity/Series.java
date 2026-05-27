package com.rocketcrew.pocat.domain.series.entity;

import com.rocketcrew.pocat.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
@Entity
@Table(name = "series",
        uniqueConstraints = @UniqueConstraint(columnNames = "name"))
public class Series extends BaseEntity {

    @Column(name = "name", nullable = false, length = 100)
    private String name;      // "Sword & Shield" (TCGdex 영문명)

    @Column(name = "name_ko", length = 500)
    private String nameKo;    // "검과방패 소드실드 소드앤실드" (공백 구분 한글 별칭)

    public void updateNameKo(String nameKo) {
        this.nameKo = nameKo;
    }
}
