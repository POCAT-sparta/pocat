package com.rocketcrew.pocat.domain.card.repository;

import com.rocketcrew.pocat.domain.card.entity.Card;
import com.rocketcrew.pocat.domain.card.entity.enums.CardGrade;
import com.rocketcrew.pocat.domain.card.entity.enums.CardStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CardRepository extends JpaRepository<Card, Long>, CardRepositoryCustom {

    Page<Card> findByUserId(Long userId, Pageable pageable);

    Page<Card> findByStatus(CardStatus status, Pageable pageable);

    Page<Card> findByUserIdAndStatus(Long userId, CardStatus status, Pageable pageable);

    boolean existsByTcgdexId(String tcgdexId);

    @Query("SELECT c FROM Card c WHERE c.status = com.rocketcrew.pocat.domain.card.entity.enums.CardStatus.ACTIVE AND (:name IS NULL OR c.name LIKE %:name%) AND (:grade IS NULL OR c.grade = :grade) ORDER BY c.createdAt DESC")
    List<Card> findActiveCardsByNameContainingAndGrade(@Param("name") String name, @Param("grade") CardGrade grade, Pageable pageable);

    @Query("SELECT c FROM Card c WHERE c.category = com.rocketcrew.pocat.domain.card.entity.enums.CardCategory.POKEMON AND c.pokemon IS NULL")
    List<Card> findPokemonCardsWithNullPokemon();
}
