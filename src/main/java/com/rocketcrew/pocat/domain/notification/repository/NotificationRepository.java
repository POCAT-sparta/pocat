package com.rocketcrew.pocat.domain.notification.repository;

import com.rocketcrew.pocat.domain.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByUserId(Long userId, Pageable pageable);

    // 커서 없을 때 (처음 조회) — pageSize+1 조회로 hasNext 정확하게 판단
    List<Notification> findTop21ByUserIdAndIsReadFalseOrderByIdDesc(Long userId);

    // 커서 있을 때 (다음 페이지) — 정렬 키(id)와 커서 키(id)를 동일하게 유지
    List<Notification> findTop21ByUserIdAndIsReadFalseAndIdLessThanOrderByIdDesc(
            Long userId, Long cursor);

    // 전체 읽음 처리용 벌크 업데이트
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.userId = :userId AND n.isRead = false AND n.deletedAt IS NULL")
    void markAllReadByUserId(@Param("userId") Long userId);

    // 전체 소프트 삭제용 — @SQLDelete를 우회하는 벌크 DELETE 대신 직접 UPDATE
    @Modifying
    @Query("UPDATE Notification n SET n.deletedAt = CURRENT_TIMESTAMP WHERE n.userId = :userId AND n.deletedAt IS NULL")
    void softDeleteAllByUserId(@Param("userId") Long userId);

    // 전체 삭제 카운트용
    int countByUserId(Long userId);
}
