package com.rocketcrew.pocat.global.init;

import com.rocketcrew.pocat.domain.auction.entity.Auction;
import com.rocketcrew.pocat.domain.auction.enums.AuctionStatus;
import com.rocketcrew.pocat.domain.auction.repository.AuctionRepository;
import com.rocketcrew.pocat.domain.card.entity.Card;
import com.rocketcrew.pocat.domain.card.entity.enums.CardCategory;
import com.rocketcrew.pocat.domain.card.entity.enums.CardGrade;
import com.rocketcrew.pocat.domain.card.entity.enums.CardSource;
import com.rocketcrew.pocat.domain.card.entity.enums.CardStatus;
import com.rocketcrew.pocat.domain.card.repository.CardRepository;
import com.rocketcrew.pocat.domain.user.entity.User;
import com.rocketcrew.pocat.domain.user.enums.UserRole;
import com.rocketcrew.pocat.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Profile("local")
@Component
@RequiredArgsConstructor
public class DataInit implements ApplicationRunner {

    private static final String ADMIN_EMAIL    = "admin@test.com";
    private static final String ADMIN_PASSWORD = "test1234";
    private static final String ADMIN_NICKNAME = "admin";

    private final UserRepository    userRepository;
    private final CardRepository    cardRepository;
    private final AuctionRepository auctionRepository;
    private final PasswordEncoder   passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<User> users = seedUsers();
        if (cardRepository.count() == 0) {
            seedCardsAndAuctions(users);
        } else if (auctionRepository.count() == 0) {
            // 카드가 이미 있는 경우(예: LFS 시드) 기존 카드로 경매만 시드
            seedAuctionsFromExistingCards(users);
        }
    }

    // ─── Users ────────────────────────────────────────────────────────────────

    private List<User> seedUsers() {
        User admin = ensureUser(ADMIN_EMAIL, ADMIN_PASSWORD, ADMIN_NICKNAME, UserRole.ADMIN, null);
        User user1 = ensureUser("user1@test.com", "test1234", "테스트유저1", UserRole.USER, "test-billing-key-1");
        User user2 = ensureUser("user2@test.com", "test1234", "테스트유저2", UserRole.USER, "test-billing-key-2");
        return List.of(admin, user1, user2);
    }

    private User ensureUser(String email, String password, String nickname, UserRole role, String billingKey) {
        if (userRepository.existsByEmail(email)) {
            return userRepository.findByEmail(email).orElseThrow();
        }
        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(password))
                .nickname(nickname)
                .userRole(role)
                .billingKey(billingKey)
                .build();
        User saved = userRepository.save(user);
        log.info("[DATA_SEEDING] 유저 생성: {} ({})", email, role);
        return saved;
    }

    // ─── Cards & Auctions ─────────────────────────────────────────────────────

    private void seedCardsAndAuctions(List<User> users) {
        User seller = users.get(1); // user1
        LocalDateTime now = LocalDateTime.now();

        Card card1 = cardRepository.save(Card.builder()
                .userId(seller.getId())
                .name("피카츄 V")
                .cardNumber("044/185")
                .rarity("V")
                .category(CardCategory.POKEMON)
                .grade(CardGrade.PSA_10)
                .source(CardSource.MANUAL)
                .status(CardStatus.ACTIVE)
                .build());

        Card card2 = cardRepository.save(Card.builder()
                .userId(seller.getId())
                .name("뮤 V")
                .cardNumber("069/185")
                .rarity("V")
                .category(CardCategory.POKEMON)
                .grade(CardGrade.PSA_9)
                .source(CardSource.MANUAL)
                .status(CardStatus.ACTIVE)
                .build());

        Card card3 = cardRepository.save(Card.builder()
                .userId(seller.getId())
                .name("박사의 연구")
                .cardNumber("178/185")
                .rarity("Uncommon")
                .category(CardCategory.TRAINERS)
                .grade(CardGrade.BGS_10)
                .source(CardSource.MANUAL)
                .status(CardStatus.ACTIVE)
                .build());

        auctionRepository.save(Auction.builder()
                .cardId(card1.getId())
                .sellerId(seller.getId())
                .title("피카츄 V PSA10 경매")
                .description("상태 좋은 피카츄 V 카드입니다.")
                .startingPrice(5_000L)
                .buyoutPrice(30_000L)
                .status(AuctionStatus.ACTIVE)
                .startedAt(now)
                .endedAt(now.plusDays(3))
                .build());

        auctionRepository.save(Auction.builder()
                .cardId(card2.getId())
                .sellerId(seller.getId())
                .title("뮤 V PSA9 경매")
                .description("뮤 V 레어 카드입니다.")
                .startingPrice(8_000L)
                .buyoutPrice(50_000L)
                .status(AuctionStatus.ACTIVE)
                .startedAt(now)
                .endedAt(now.plusDays(5))
                .build());

        auctionRepository.save(Auction.builder()
                .cardId(card3.getId())
                .sellerId(seller.getId())
                .title("박사의 연구 BGS10 경매")
                .description("트레이너 카드입니다.")
                .startingPrice(1_000L)
                .buyoutPrice(5_000L)
                .status(AuctionStatus.ACTIVE)
                .startedAt(now)
                .endedAt(now.plusDays(7))
                .build());

        log.info("[DATA_SEEDING] 카드 3장, 경매 3건 시드 완료 (판매자: {})", seller.getEmail());
    }

    private void seedAuctionsFromExistingCards(List<User> users) {
        User seller = users.get(1); // user1
        List<Card> cards = cardRepository
                .findAll(PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "id")))
                .stream()
                .filter(c -> c.getStatus() == CardStatus.ACTIVE)
                .limit(3)
                .toList();

        if (cards.isEmpty()) {
            log.info("[DATA_SEEDING] 시드용 카드 없음, 경매 시드 건너뜁니다.");
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < cards.size(); i++) {
            Card card = cards.get(i);
            auctionRepository.save(Auction.builder()
                    .cardId(card.getId())
                    .sellerId(seller.getId())
                    .title(card.getName() + " 경매")
                    .description("테스트 경매입니다.")
                    .startingPrice(5_000L)
                    .buyoutPrice(50_000L)
                    .status(AuctionStatus.ACTIVE)
                    .startedAt(now)
                    .endedAt(now.plusDays(3L + i * 2L))
                    .build());
        }
        log.info("[DATA_SEEDING] 기존 카드 {}장으로 경매 {}건 시드 완료 (판매자: {})",
                cards.size(), cards.size(), seller.getEmail());
    }
}
