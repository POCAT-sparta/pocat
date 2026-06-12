package com.rocketcrew.pocat.domain.auction.service;

import com.rocketcrew.pocat.cache.MockRedisTestConfig;
import com.rocketcrew.pocat.support.MockElasticsearchTestConfig;
import com.rocketcrew.pocat.domain.auction.entity.Auction;
import com.rocketcrew.pocat.domain.auction.enums.AuctionStatus;
import com.rocketcrew.pocat.domain.auction.kafka.AuctionEventHandler;
import com.rocketcrew.pocat.domain.auction.repository.AuctionRepository;
import com.rocketcrew.pocat.domain.auction.repository.AuctionSearchRepository;
import com.rocketcrew.pocat.domain.bid.service.BidEventHandler;
import com.rocketcrew.pocat.domain.card.entity.Card;
import com.rocketcrew.pocat.domain.card.entity.enums.CardCategory;
import com.rocketcrew.pocat.domain.card.entity.enums.CardGrade;
import com.rocketcrew.pocat.domain.card.entity.enums.CardSource;
import com.rocketcrew.pocat.domain.card.entity.enums.CardStatus;
import com.rocketcrew.pocat.domain.card.repository.CardRepository;
import com.rocketcrew.pocat.domain.notification.service.NotificationEventHandler;
import com.rocketcrew.pocat.domain.order.service.OrderEventHandler;
import com.rocketcrew.pocat.domain.payment.client.out.kafka.handler.PaymentEventHandler;
import com.rocketcrew.pocat.domain.refund.service.RefundEventHandler;
import com.rocketcrew.pocat.domain.series.entity.Series;
import com.rocketcrew.pocat.domain.series.repository.SeriesRepository;
import com.rocketcrew.pocat.domain.set.entity.PokemonSet;
import com.rocketcrew.pocat.domain.set.repository.PokemonSetRepository;
import com.rocketcrew.pocat.domain.settlement.service.SettlementEventHandler;
import com.rocketcrew.pocat.domain.user.entity.User;
import com.rocketcrew.pocat.domain.user.enums.UserRole;
import com.rocketcrew.pocat.domain.user.repository.UserRepository;
import com.rocketcrew.pocat.global.outbox.service.OutboxEventWriter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * #219: spring.jpa.open-in-view=false 전환 회귀 테스트
 *
 * <p>{@code AuctionLifecycleService.activateApprovedAuction()}은 ACTIVE 전환 트랜잭션의
 * afterCommit 콜백에서 {@code AuctionEsIndexService.index()}를 호출한다.
 * open-in-view=false 환경에서는 이 afterCommit 콜백이 트랜잭션·영속성 컨텍스트가 모두 종료된
 * 이후에 실행되므로, index() 내부에서 detached Card의 LAZY 연관(pokemon)에 접근할 때
 * LazyInitializationException이 발생하지 않아야 한다.
 *
 * <p><b>클래스/메서드에 @Transactional을 절대 사용하지 않는다</b> — 테스트 트랜잭션이 걸리면
 * afterCommit 콜백이 즉시 실행되지 않거나 영속성 컨텍스트가 종료되지 않아 open-in-view=false의
 * 실제 운영 시나리오를 재현할 수 없다. 대신 TransactionTemplate으로 별도 트랜잭션을 커밋한다.
 */
@SpringBootTest
@Import({MockRedisTestConfig.class, MockElasticsearchTestConfig.class})
@DisplayName("AuctionLifecycleService - activateApprovedAuction() ES 인덱싱 open-in-view=false 회귀 테스트")
class AuctionLifecycleEsIndexIntegrationTest {

    // ── 외부 의존성 Mock (AuctionBuyoutConcurrencyIntegrationTest와 동일한 격리 패턴) ──
    @MockBean private StringRedisTemplate stringRedisTemplate;
    @MockBean private RedissonClient redissonClient;
    @MockBean private AuctionSearchRepository auctionSearchRepository;
    @MockBean private RedisConnectionFactory redisConnectionFactory;
    @MockBean private RedisMessageListenerContainer redisMessageListenerContainer;
    @MockBean private OutboxEventWriter outboxEventWriter;
    @MockBean private AuctionEventHandler auctionEventHandler;
    @MockBean private BidEventHandler bidEventHandler;
    @MockBean private OrderEventHandler orderEventHandler;
    @MockBean private PaymentEventHandler paymentEventHandler;
    @MockBean private RefundEventHandler refundEventHandler;
    @MockBean private SettlementEventHandler settlementEventHandler;
    @MockBean private NotificationEventHandler notificationEventHandler;

    // ── 실제 빈 (AuctionEsIndexService도 실제 빈을 사용해 LIE 발생 여부를 검증) ──
    @Autowired private AuctionLifecycleService auctionLifecycleService;
    @Autowired private AuctionRepository auctionRepository;
    @Autowired private CardRepository cardRepository;
    @Autowired private SeriesRepository seriesRepository;
    @Autowired private PokemonSetRepository pokemonSetRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    private static final String SELLER_EMAIL = "it-lifecycle-esindex-seller@test.com";

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM auctions WHERE seller_id IN (SELECT id FROM users WHERE email = ?)", SELLER_EMAIL);
        jdbcTemplate.update("DELETE FROM cards WHERE user_id IN (SELECT id FROM users WHERE email = ?)", SELLER_EMAIL);
        jdbcTemplate.update("DELETE FROM users WHERE email = ?", SELLER_EMAIL);
    }

    @Test
    @DisplayName("activateApprovedAuction() 호출 후 커밋 → afterCommit의 index() 호출이 LIE 없이 완료되고 ACTIVE로 전환된다")
    void activateApprovedAuction_afterCommit_indexesWithoutLazyInitializationException() throws InterruptedException {
        // given: Redisson 락은 항상 성공하도록 mock
        RLock mockLock = mock(RLock.class);
        when(redissonClient.getLock(anyString())).thenReturn(mockLock);
        when(mockLock.tryLock(anyLong(), any(java.util.concurrent.TimeUnit.class))).thenReturn(true);
        when(mockLock.isHeldByCurrentThread()).thenReturn(true);
        doAnswer(inv -> null).when(mockLock).unlock();

        Long[] auctionIdHolder = new Long[1];
        transactionTemplate.execute(status -> {
            User seller = userRepository.save(User.builder()
                    .email(SELLER_EMAIL)
                    .password("encoded-pw")
                    .nickname("라이프사이클ES판매자")
                    .userRole(UserRole.USER)
                    .build());

            Series series = seriesRepository.findByName("Sword & Shield")
                    .orElseGet(() -> seriesRepository.save(Series.builder()
                            .name("Sword & Shield")
                            .build()));

            PokemonSet pokemonSet = pokemonSetRepository.findBySetId("swsh5")
                    .orElseGet(() -> pokemonSetRepository.save(PokemonSet.builder()
                            .setId("swsh5")
                            .name("Rebel Clash")
                            .series(series)
                            .build()));

            Card card = cardRepository.save(Card.builder()
                    .userId(seller.getId())
                    .tcgdexId("swsh5-lifecycle-lazy-test")
                    .name("라이프사이클 테스트 카드")
                    .series(series)
                    .pokemonSet(pokemonSet)
                    .pokemon(null)
                    .cardNumber("101")
                    .rarity("Common")
                    .category(CardCategory.TRAINERS)
                    .grade(CardGrade.PSA_10)
                    .imageUrl("https://example.com/lifecycle.jpg")
                    .source(CardSource.TCGDEX)
                    .status(CardStatus.ACTIVE)
                    .build());

            Auction auction = auctionRepository.save(Auction.builder()
                    .cardId(card.getId())
                    .sellerId(seller.getId())
                    .title("라이프사이클 ES 인덱싱 LIE 회귀 테스트")
                    .description("테스트용")
                    .startingPrice(10_000L)
                    .status(AuctionStatus.APPROVED)
                    .build());

            auctionIdHolder[0] = auction.getId();
            return null;
        });

        Long auctionId = auctionIdHolder[0];

        // when: activateApprovedAuction()을 별도 트랜잭션으로 실행 → 커밋 시 afterCommit 콜백 실행
        Boolean activated = transactionTemplate.execute(status ->
                auctionLifecycleService.activateApprovedAuction(auctionId));

        // then: 예외 없이 완료
        assertThat(activated).isTrue();

        // 트랜잭션이 끝난 뒤(afterCommit 콜백 실행 후) 상태 재조회
        AuctionStatus status = transactionTemplate.execute(s ->
                auctionRepository.findById(auctionId).orElseThrow().getStatus());
        assertThat(status).isEqualTo(AuctionStatus.ACTIVE);

        // afterCommit 콜백에서 AuctionEsIndexService.index() → auctionSearchRepository.save()가
        // 예외 없이 호출되었어야 함 (index()는 내부에서 예외를 흡수하므로, save 호출 자체로 검증)
        verify(auctionSearchRepository).save(any());
    }

    @Test
    @DisplayName("activateApprovedAuction() 직접 호출은 예외를 던지지 않는다 (afterCommit 콜백 내 LIE를 흡수하지 않고 안전하게 동작)")
    void activateApprovedAuction_doesNotThrowEvenWithLazyAssociations() {
        Long[] auctionIdHolder = new Long[1];
        transactionTemplate.execute(status -> {
            User seller = userRepository.save(User.builder()
                    .email(SELLER_EMAIL)
                    .password("encoded-pw")
                    .nickname("라이프사이클ES판매자2")
                    .userRole(UserRole.USER)
                    .build());

            Series series = seriesRepository.findByName("Sword & Shield")
                    .orElseGet(() -> seriesRepository.save(Series.builder()
                            .name("Sword & Shield")
                            .build()));

            PokemonSet pokemonSet = pokemonSetRepository.findBySetId("swsh5")
                    .orElseGet(() -> pokemonSetRepository.save(PokemonSet.builder()
                            .setId("swsh5")
                            .name("Rebel Clash")
                            .series(series)
                            .build()));

            Card card = cardRepository.save(Card.builder()
                    .userId(seller.getId())
                    .tcgdexId("swsh5-lifecycle-lazy-test-2")
                    .name("라이프사이클 테스트 카드2")
                    .series(series)
                    .pokemonSet(pokemonSet)
                    .pokemon(null)
                    .cardNumber("102")
                    .rarity("Common")
                    .category(CardCategory.TRAINERS)
                    .grade(CardGrade.PSA_10)
                    .imageUrl("https://example.com/lifecycle2.jpg")
                    .source(CardSource.TCGDEX)
                    .status(CardStatus.ACTIVE)
                    .build());

            Auction auction = auctionRepository.save(Auction.builder()
                    .cardId(card.getId())
                    .sellerId(seller.getId())
                    .title("라이프사이클 ES 인덱싱 LIE 회귀 테스트2")
                    .description("테스트용")
                    .startingPrice(10_000L)
                    .status(AuctionStatus.APPROVED)
                    .build());

            auctionIdHolder[0] = auction.getId();
            return null;
        });

        Long auctionId = auctionIdHolder[0];

        RLock mockLock = mock(RLock.class);
        when(redissonClient.getLock(anyString())).thenReturn(mockLock);
        try {
            when(mockLock.tryLock(anyLong(), any(java.util.concurrent.TimeUnit.class))).thenReturn(true);
        } catch (InterruptedException ignored) {
            // mock setup - never actually thrown
        }
        when(mockLock.isHeldByCurrentThread()).thenReturn(true);
        doAnswer(inv -> null).when(mockLock).unlock();

        assertThatCode(() -> transactionTemplate.execute(status ->
                auctionLifecycleService.activateApprovedAuction(auctionId)))
                .doesNotThrowAnyException();
    }
}
