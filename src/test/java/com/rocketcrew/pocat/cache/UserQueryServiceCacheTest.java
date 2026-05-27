package com.rocketcrew.pocat.cache;

import com.rocketcrew.pocat.domain.user.dto.request.UpdateUserRequest;
import com.rocketcrew.pocat.domain.user.dto.response.UserResponse;
import com.rocketcrew.pocat.domain.user.entity.User;
import com.rocketcrew.pocat.domain.user.enums.UserRole;
import com.rocketcrew.pocat.domain.user.repository.UserRepository;
import com.rocketcrew.pocat.domain.user.service.UserCommandService;
import com.rocketcrew.pocat.domain.user.service.UserQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * T-02: UserQueryService.getUserById() 캐싱 검증
 * T-03: UserCommandService.updateUser() 캐시 Evict 검증
 *
 * T-02: 동일 userId로 2회 호출 시 UserRepository.findById()가 1회만 호출되는지
 *       현재 @Cacheable 없으므로 → 2회 호출 → verify(1회) FAIL 예상
 *
 * T-03: getUserById() 후 캐시에 데이터 존재 여부 확인 + updateUser() 후 캐시 Evict 확인
 *       현재 @Cacheable/@CacheEvict 미구현 → 캐시 미저장 → FAIL 예상
 *
 * @MockBean으로 Redis 인프라 빈을 교체 → 실제 Redis 연결 없이 Context 기동.
 * MockRedisTestConfig에서 ConcurrentMapCacheManager 제공.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Import(MockRedisTestConfig.class)
class UserQueryServiceCacheTest {

    // Redis 인프라 빈을 @MockBean으로 교체 (실제 연결 방지)
    @MockBean
    private RedissonClient redissonClient;

    @MockBean
    private RedisConnectionFactory redisConnectionFactory;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @MockBean
    private RedisMessageListenerContainer redisMessageListenerContainer;

    @Autowired
    private UserQueryService userQueryService;

    @Autowired
    private UserCommandService userCommandService;

    @SpyBean
    private UserRepository userRepository;

    @Autowired
    private CacheManager cacheManager;

    private User testUser;

    @BeforeEach
    void setUp() {
        // 캐시 초기화 (테스트 격리)
        Cache userProfileCache = cacheManager.getCache("user:profile");
        if (userProfileCache != null) {
            userProfileCache.clear();
        }

        // 테스트용 User 엔티티 생성 및 저장
        testUser = User.builder()
                .email("cache-test-" + System.nanoTime() + "@pocat.com")
                .password("encoded-password")
                .nickname("CacheTester")
                .userRole(UserRole.USER)
                .build();
        testUser = userRepository.save(testUser);

        // SpyBean 호출 카운트 초기화 (save 호출 분리)
        clearInvocations(userRepository);
    }

    @Test
    @DisplayName("T-02: 동일 userId로 getUserById() 2회 호출 시 Repository.findById()는 1회만 호출되어야 한다 (@Cacheable 검증)")
    void getUserById_calledTwiceWithSameId_shouldHitCacheAndCallRepositoryOnce() {
        Long userId = testUser.getId();

        // 첫 번째 호출 - DB 조회 발생 및 캐시 저장 (캐싱 구현 시)
        UserResponse first = userQueryService.getUserById(userId);
        // 두 번째 호출 - 캐시 히트 → Repository 호출 없어야 함 (캐싱 구현 시)
        UserResponse second = userQueryService.getUserById(userId);

        // @Cacheable 미구현 시 findById는 2회 호출됨 → verify(1회) FAIL
        verify(userRepository, times(1)).findById(userId);
    }

    @Test
    @DisplayName("T-03: getUserById() 후 캐시에 데이터가 저장되고, updateUser() 후 캐시가 Evict되어야 한다 (@Cacheable + @CacheEvict 검증)")
    void getUserById_shouldPopulateCache_andUpdateUser_shouldEvictCache() {
        Long userId = testUser.getId();
        Cache userProfileCache = cacheManager.getCache("user:profile");

        // 1) 첫 번째 조회 → @Cacheable: 캐시에 저장되어야 함
        userQueryService.getUserById(userId);

        // @Cacheable 미구현 시 캐시에 아무것도 저장되지 않음 → null → FAIL
        assertThat(userProfileCache)
                .as("user:profile 캐시가 존재해야 한다")
                .isNotNull();
        assertThat(userProfileCache.get(userId))
                .as("getUserById() 호출 후 userId=%d 에 해당하는 캐시 엔트리가 존재해야 한다 (현재 @Cacheable 미구현 → null)", userId)
                .isNotNull();

        // 2) 업데이트 → @CacheEvict: 캐시 무효화 되어야 함
        UpdateUserRequest updateRequest = new UpdateUserRequest("UpdatedNick", null, null);
        userCommandService.updateUser(userId, updateRequest);

        // @CacheEvict 미구현 시 캐시 엔트리가 남아있음 → isNull() 실패 → FAIL
        // (@Cacheable 구현 + @CacheEvict 미구현 시)
        assertThat(userProfileCache.get(userId))
                .as("updateUser() 호출 후 userId=%d 의 캐시 엔트리가 Evict되어 null이어야 한다 (현재 @CacheEvict 미구현)", userId)
                .isNull();
    }
}
