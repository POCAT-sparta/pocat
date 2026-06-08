package com.rocketcrew.pocat.cache;

import com.rocketcrew.pocat.domain.comment.entity.Comment;
import com.rocketcrew.pocat.domain.comment.repository.CommentRepository;
import com.rocketcrew.pocat.domain.comment.service.CommentQueryService;
import com.rocketcrew.pocat.domain.user.entity.User;
import com.rocketcrew.pocat.domain.user.enums.UserRole;
import com.rocketcrew.pocat.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import com.rocketcrew.pocat.domain.auction.service.AuctionEsIndexService;
import com.rocketcrew.pocat.support.MockElasticsearchTestConfig;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.test.context.ActiveProfiles;

import static org.mockito.Mockito.*;

/**
 * T-04: CommentQueryService 캐싱 검증
 *
 * 동일 postId로 getCommentsByPost() 2회 호출 시 CommentRepository 1회만 호출
 * 현재 @Cacheable 없으므로 → 2회 호출 → verify(1회) FAIL 예상
 *
 * @MockBean으로 Redis 인프라 빈을 교체 → 실제 Redis 연결 없이 Context 기동.
 * MockRedisTestConfig에서 ConcurrentMapCacheManager 제공.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Import({MockRedisTestConfig.class, MockElasticsearchTestConfig.class})
class CommentCacheTest {

    // Redis 인프라 빈을 @MockBean으로 교체 (실제 연결 방지)
    @MockBean
    private RedissonClient redissonClient;

    @MockBean
    private RedisConnectionFactory redisConnectionFactory;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @MockBean
    private RedisMessageListenerContainer redisMessageListenerContainer;

    @MockBean
    private AuctionEsIndexService auctionEsIndexService;

    @Autowired
    private CommentQueryService commentQueryService;

    @SpyBean
    private CommentRepository commentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CacheManager cacheManager;

    private static final Long TEST_POST_ID = 999L;

    @BeforeEach
    void setUp() {
        // 캐시 초기화 (테스트 격리)
        if (cacheManager.getCache("post:comments") != null) {
            cacheManager.getCache("post:comments").clear();
        }

        // 기존 데이터 정리
        commentRepository.deleteAll();

        // 테스트용 User 저장
        User user = User.builder()
                .email("comment-cache-test-" + System.nanoTime() + "@pocat.com")
                .password("encoded-password")
                .nickname("CommentCacheTester")
                .userRole(UserRole.USER)
                .build();
        user = userRepository.save(user);

        // 테스트용 Comment 저장
        Comment comment = Comment.builder()
                .userId(user.getId())
                .freePostId(TEST_POST_ID)
                .content("테스트 댓글 내용")
                .build();
        commentRepository.save(comment);

        // SpyBean 카운트 초기화 (setUp의 save/deleteAll 호출 분리)
        clearInvocations(commentRepository);
    }

    @Test
    @DisplayName("T-04: 동일 postId로 getCommentsByPost() 2회 호출 시 CommentRepository.findByFreePostIdAndParentIdIsNull()은 1회만 호출되어야 한다 (@Cacheable 검증)")
    void getCommentsByPost_calledTwiceWithSamePostId_shouldHitCacheAndCallRepositoryOnce() {
        Pageable pageable = PageRequest.of(0, 10);

        // 첫 번째 호출 - DB 조회 발생 및 캐시 저장 (캐싱 구현 시)
        commentQueryService.getCommentsByPost(TEST_POST_ID, pageable);
        // 두 번째 호출 - 캐시 히트 → Repository 호출 없어야 함 (캐싱 구현 시)
        commentQueryService.getCommentsByPost(TEST_POST_ID, pageable);

        // @Cacheable 미구현 시 findByFreePostIdAndParentIdIsNull은 2회 호출됨 → FAIL
        verify(commentRepository, times(1))
                .findByFreePostIdAndParentIdIsNull(TEST_POST_ID, pageable);
    }
}
