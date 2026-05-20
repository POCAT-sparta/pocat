package com.rocketcrew.pocat.domain.user.repository;

import com.rocketcrew.pocat.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>, UserRepositoryCustom {

    Optional<User> findByEmail(String email);

    Optional<User> findFirstByOrderByIdAsc();

    boolean existsByEmail(String email);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE User u SET u.billingKey = :billingKey WHERE u.id = :userId AND u.billingKey IS NULL")
    int updateBillingKeyIfNull(@Param("userId") Long userId, @Param("billingKey") String billingKey);
}
