package com.rocketcrew.pocat.domain.series.repository;

import com.rocketcrew.pocat.domain.series.entity.Series;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SeriesRepository extends JpaRepository<Series, Long> {
    Optional<Series> findByName(String name);
    boolean existsByName(String name);
}
