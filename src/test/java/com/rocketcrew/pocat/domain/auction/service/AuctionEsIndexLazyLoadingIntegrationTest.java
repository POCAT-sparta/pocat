package com.rocketcrew.pocat.domain.auction.service;

import com.rocketcrew.pocat.cache.MockRedisTestConfig;
import com.rocketcrew.pocat.support.MockElasticsearchTestConfig;
import com.rocketcrew.pocat.domain.auction.document.AuctionDocument;
import com.rocketcrew.pocat.domain.auction.entity.Auction;
import com.rocketcrew.pocat.domain.auction.enums.AuctionStatus;
import com.rocketcrew.pocat.domain.auction.repository.AuctionRepository;
import com.rocketcrew.pocat.domain.auction.repository.AuctionSearchRepository;
import com.rocketcrew.pocat.domain.card.entity.Card;
import com.rocketcrew.pocat.domain.card.entity.enums.CardCategory;
import com.rocketcrew.pocat.domain.card.entity.enums.CardGrade;
import com.rocketcrew.pocat.domain.card.entity.enums.CardSource;
import com.rocketcrew.pocat.domain.card.entity.enums.CardStatus;
import com.rocketcrew.pocat.domain.card.repository.CardRepository;
import com.rocketcrew.pocat.domain.pokemon.entity.Pokemon;
import com.rocketcrew.pocat.domain.pokemon.repository.PokemonRepository;
import com.rocketcrew.pocat.domain.series.entity.Series;
import com.rocketcrew.pocat.domain.series.repository.SeriesRepository;
import com.rocketcrew.pocat.domain.set.entity.PokemonSet;
import com.rocketcrew.pocat.domain.set.repository.PokemonSetRepository;
import com.rocketcrew.pocat.domain.user.entity.User;
import com.rocketcrew.pocat.domain.user.enums.UserRole;
import com.rocketcrew.pocat.domain.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.verify;

/**
 * #219: spring.jpa.open-in-view=false 전환 회귀 테스트
 *
 * <p>{@code AuctionEsIndexService.index()}는 트랜잭션 밖(예: afterCommit 콜백)에서 호출되므로,
 * open-in-view=false 환경에서는 영속성 컨텍스트가 이미 닫혀 있는 detached Card 엔티티에 대해
 * {@code card.getPokemon()} (LAZY 연관)을 호출하면 LazyInitializationException이 발생할 수 있다.
 *
 * <p>이 테스트는 실제 DB(H2)에 pokemon이 연결된 Card + Auction을 별도 트랜잭션으로 저장·커밋한 뒤,
 * 트랜잭션 밖에서 {@code auctionEsIndexService.index(auction)}을 직접 호출해
 * 예외 없이 완료되고 cardNameKo가 올바르게 채워지는지 검증한다.
 *
 * <p><b>주의</b>: 현재 {@code AuctionEsIndexService.index()}는 {@code cardRepository.findById()}를 사용한다.
 * open-in-view=false(T1) 적용 후, BACKEND가 이를 {@code cardRepository.findByIdWithPokemon()}
 * (fetch join)으로 교체해야 이 테스트가 GREEN이 된다. 메서드가 존재하지 않으면 컴파일 에러가 발생한다.
 */
@SpringBootTest
@Import({MockRedisTestConfig.class, MockElasticsearchTestConfig.class})
@DisplayName("AuctionEsIndexService - open-in-view=false 지연 로딩 회귀 테스트")
class AuctionEsIndexLazyLoadingIntegrationTest {

    @MockBean private AuctionSearchRepository auctionSearchRepository;
    @MockBean private StringRedisTemplate stringRedisTemplate;
    @MockBean private RedissonClient redissonClient;
    @MockBean private RedisConnectionFactory redisConnectionFactory;
    @MockBean private RedisMessageListenerContainer redisMessageListenerContainer;

    @Autowired private AuctionEsIndexService auctionEsIndexService;
    @Autowired private AuctionRepository auctionRepository;
    @Autowired private CardRepository cardRepository;
    @Autowired private PokemonRepository pokemonRepository;
    @Autowired private SeriesRepository seriesRepository;
    @Autowired private PokemonSetRepository pokemonSetRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    private static final String SELLER_EMAIL = "it-esindex-seller@test.com";

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM auctions WHERE seller_id IN (SELECT id FROM users WHERE email = ?)", SELLER_EMAIL);
        jdbcTemplate.update("DELETE FROM cards WHERE user_id IN (SELECT id FROM users WHERE email = ?)", SELLER_EMAIL);
        jdbcTemplate.update("DELETE FROM users WHERE email = ?", SELLER_EMAIL);
    }

    @Test
    @DisplayName("pokemon이 연결된 Card: 트랜잭션 밖에서 index() 호출 시 LazyInitializationException 없이 cardNameKo가 채워진다")
    void index_withPokemonLinkedCard_completesWithoutLazyInitializationException() {
        // given: 별도 트랜잭션으로 User + Pokemon + Card + Auction 저장 후 커밋
        Long[] auctionIdHolder = new Long[1];
        transactionTemplate.execute(status -> {
            User seller = userRepository.save(User.builder()
                    .email(SELLER_EMAIL)
                    .password("encoded-pw")
                    .nickname("ES인덱싱판매자")
                    .userRole(UserRole.USER)
                    .build());

            Pokemon pokemon = pokemonRepository.findByName("Pikachu")
                    .orElseGet(() -> pokemonRepository.save(Pokemon.builder()
                            .name("Pikachu")
                            .nameKo("피카츄")
                            .build()));

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
                    .tcgdexId("swsh5-58-lazy-test")
                    .name("피카츄")
                    .series(series)
                    .pokemonSet(pokemonSet)
                    .pokemon(pokemon)
                    .cardNumber("058")
                    .rarity("Rare")
                    .category(CardCategory.POKEMON)
                    .grade(CardGrade.PSA_10)
                    .imageUrl("https://example.com/pikachu.jpg")
                    .source(CardSource.TCGDEX)
                    .status(CardStatus.ACTIVE)
                    .build());

            Auction auction = auctionRepository.save(Auction.builder()
                    .cardId(card.getId())
                    .sellerId(seller.getId())
                    .title("ES 인덱싱 LIE 회귀 테스트")
                    .description("테스트용")
                    .startingPrice(10_000L)
                    .status(AuctionStatus.ACTIVE)
                    .build());

            auctionIdHolder[0] = auction.getId();
            return null;
        });

        Long auctionId = auctionIdHolder[0];
        Auction detachedAuction = transactionTemplate.execute(status ->
                auctionRepository.findById(auctionId).orElseThrow());

        // when: 트랜잭션 밖에서 직접 index() 호출
        assertThatCode(() -> auctionEsIndexService.index(detachedAuction))
                .as("open-in-view=false 환경에서 index() 호출 시 LazyInitializationException이 발생하지 않아야 함")
                .doesNotThrowAnyException();

        // then: AuctionSearchRepository.save가 호출되고, 캡처된 문서의 cardNameKo == pokemon.nameKo
        ArgumentCaptor<AuctionDocument> docCaptor = ArgumentCaptor.forClass(AuctionDocument.class);
        verify(auctionSearchRepository).save(docCaptor.capture());

        AuctionDocument savedDoc = docCaptor.getValue();
        assertThat(savedDoc.getCardNameKo()).isEqualTo("피카츄");
    }

    @Test
    @DisplayName("pokemon이 null인 Card: 트랜잭션 밖에서 index() 호출 시 LIE 없이 완료되고 cardNameKo는 null")
    void index_withoutPokemon_completesWithoutLazyInitializationException() {
        // given: pokemon == null인 Card + Auction을 별도 트랜잭션으로 저장 후 커밋
        Long[] auctionIdHolder = new Long[1];
        transactionTemplate.execute(status -> {
            User seller = userRepository.save(User.builder()
                    .email(SELLER_EMAIL)
                    .password("encoded-pw")
                    .nickname("ES인덱싱판매자")
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
                    .tcgdexId("swsh5-trainer-lazy-test")
                    .name("트레이너 카드")
                    .series(series)
                    .pokemonSet(pokemonSet)
                    .pokemon(null)
                    .cardNumber("100")
                    .rarity("Common")
                    .category(CardCategory.TRAINERS)
                    .grade(CardGrade.PSA_10)
                    .imageUrl("https://example.com/trainer.jpg")
                    .source(CardSource.TCGDEX)
                    .status(CardStatus.ACTIVE)
                    .build());

            Auction auction = auctionRepository.save(Auction.builder()
                    .cardId(card.getId())
                    .sellerId(seller.getId())
                    .title("ES 인덱싱 LIE 회귀 테스트 (pokemon null)")
                    .description("테스트용")
                    .startingPrice(10_000L)
                    .status(AuctionStatus.ACTIVE)
                    .build());

            auctionIdHolder[0] = auction.getId();
            return null;
        });

        Long auctionId = auctionIdHolder[0];
        Auction detachedAuction = transactionTemplate.execute(status ->
                auctionRepository.findById(auctionId).orElseThrow());

        // when
        assertThatCode(() -> auctionEsIndexService.index(detachedAuction))
                .doesNotThrowAnyException();

        // then
        ArgumentCaptor<AuctionDocument> docCaptor = ArgumentCaptor.forClass(AuctionDocument.class);
        verify(auctionSearchRepository).save(docCaptor.capture());

        AuctionDocument savedDoc = docCaptor.getValue();
        assertThat(savedDoc.getCardNameKo()).isNull();
    }
}
