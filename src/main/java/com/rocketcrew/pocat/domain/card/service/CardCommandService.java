package com.rocketcrew.pocat.domain.card.service;

import com.rocketcrew.pocat.domain.ai.rag.event.CardEmbeddingEvent;
import com.rocketcrew.pocat.domain.card.document.CardDocument;
import com.rocketcrew.pocat.domain.card.dto.request.CreateCardRequest;
import com.rocketcrew.pocat.domain.card.dto.request.UpdateCardRequest;
import com.rocketcrew.pocat.domain.card.dto.response.CardResponse;
import com.rocketcrew.pocat.domain.card.entity.Card;
import com.rocketcrew.pocat.domain.card.entity.enums.CardCategory;
import com.rocketcrew.pocat.domain.card.entity.enums.CardStatus;
import com.rocketcrew.pocat.domain.card.repository.CardRepository;
import com.rocketcrew.pocat.domain.card.repository.CardSearchRepository;
import com.rocketcrew.pocat.domain.pokemon.entity.Pokemon;
import com.rocketcrew.pocat.domain.pokemon.service.PokemonCommandService;
import com.rocketcrew.pocat.domain.series.entity.Series;
import com.rocketcrew.pocat.domain.series.service.SeriesCommandService;
import com.rocketcrew.pocat.domain.set.entity.PokemonSet;
import com.rocketcrew.pocat.domain.set.service.PokemonSetCommandService;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.CardException;
import com.rocketcrew.pocat.global.infra.s3.S3ImageDownloader;
import com.rocketcrew.pocat.global.infra.s3.S3Uploader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CardCommandService {

    private final CardRepository cardRepository;
    private final CardSearchRepository cardSearchRepository;
    private final SeriesCommandService seriesCommandService;
    private final PokemonSetCommandService pokemonSetCommandService;
    private final PokemonCommandService pokemonCommandService;
    private final ApplicationEventPublisher eventPublisher;
    private final S3Uploader s3Uploader;
    private final S3ImageDownloader s3ImageDownloader;

    public CardResponse createCard(Long userId, CreateCardRequest request) {
        if (request.tcgdexId() != null && cardRepository.existsByTcgdexId(request.tcgdexId())) {
            throw new CardException(ErrorCode.CARD_ALREADY_EXISTS);
        }

        Series series = seriesCommandService.findOrCreate(request.series());
        PokemonSet pokemonSet = pokemonSetCommandService.findOrCreate(
                request.setId(), request.setName(), series);
        Pokemon pokemon = null;
        if (request.category() == CardCategory.POKEMON) {
            pokemon = pokemonCommandService.findOrCreateForCardName(request.name()).orElse(null);
        }

        Card card = Card.builder()
                .userId(userId)
                .tcgdexId(request.tcgdexId())
                .name(request.name())
                .series(series)
                .pokemonSet(pokemonSet)
                .pokemon(pokemon)
                .cardNumber(request.cardNumber())
                .rarity(request.rarity())
                .category(request.category())
                .grade(request.grade())
                .imageUrl(request.imageUrl())
                .source(request.source())
                .status(CardStatus.PENDING)
                .build();
        try {
            return CardResponse.from(cardRepository.save(card));
        } catch (DataIntegrityViolationException e) {
            throw new CardException(ErrorCode.CARD_ALREADY_EXISTS);
        }
    }

    public CardResponse createCardWithImage(Long userId, CreateCardRequest request, MultipartFile image) {
        if (request.tcgdexId() != null && cardRepository.existsByTcgdexId(request.tcgdexId())) {
            throw new CardException(ErrorCode.CARD_ALREADY_EXISTS);
        }

        Series series = seriesCommandService.findOrCreate(request.series());
        PokemonSet pokemonSet = pokemonSetCommandService.findOrCreate(
                request.setId(), request.setName(), series);
        Pokemon pokemon = null;
        if (request.category() == CardCategory.POKEMON) {
            pokemon = pokemonCommandService.findOrCreateForCardName(request.name()).orElse(null);
        }

        Card card = Card.builder()
                .userId(userId)
                .tcgdexId(request.tcgdexId())
                .name(request.name())
                .series(series)
                .pokemonSet(pokemonSet)
                .pokemon(pokemon)
                .cardNumber(request.cardNumber())
                .rarity(request.rarity())
                .category(request.category())
                .grade(request.grade())
                .imageUrl(null)
                .source(request.source())
                .status(CardStatus.PENDING)
                .build();
        try {
            card = cardRepository.save(card);
        } catch (DataIntegrityViolationException e) {
            throw new CardException(ErrorCode.CARD_ALREADY_EXISTS);
        }

        try {
            byte[] bytes = image.getBytes();
            String contentType = image.getContentType() != null ? image.getContentType() : "image/jpeg";
            String s3Url = s3Uploader.upload(S3Uploader.cardPendingImageKey(card.getId()), bytes, contentType);
            card.updateImageUrl(s3Url);
        } catch (Exception e) {
            log.warn("[CardCommandService] 이미지 업로드 실패 cardId={}", card.getId(), e);
            throw new CardException(ErrorCode.CARD_IMAGE_DOWNLOAD_FAILED);
        }

        return CardResponse.from(card);
    }

    public CardResponse updateCard(Long id, UpdateCardRequest request) {
        Card card = cardRepository.findById(id)
                .orElseThrow(() -> new CardException(ErrorCode.CARD_NOT_FOUND));

        Series series = null;
        if (request.series() != null) {
            series = seriesCommandService.findOrCreate(request.series());
        }
        PokemonSet pokemonSet = null;
        if (request.setId() != null) {
            String setName = request.setName() != null ? request.setName()
                    : (card.getPokemonSet() != null ? card.getPokemonSet().getName() : request.setId());
            pokemonSet = pokemonSetCommandService.findOrCreate(request.setId(), setName, series);
        }

        card.update(request.tcgdexId(), request.name(), series, pokemonSet,
                request.cardNumber(), request.rarity(), request.category(),
                request.grade(), request.imageUrl(), request.source());

        if (card.getStatus() == CardStatus.ACTIVE) {
            indexCard(card);
            String cardText = card.getName() + " " + card.getGrade() + " " + card.getSeries();
            eventPublisher.publishEvent(new CardEmbeddingEvent(card.getId(), cardText));
        }
        return CardResponse.from(card);
    }

    public void deleteCard(Long id) {
        Card card = cardRepository.findById(id)
                .orElseThrow(() -> new CardException(ErrorCode.CARD_NOT_FOUND));
        cardRepository.delete(card);
        deleteCardIndex(id);
    }

    public CardResponse approveCard(Long id) {
        Card card = cardRepository.findById(id)
                .orElseThrow(() -> new CardException(ErrorCode.CARD_NOT_FOUND));
        card.approve();

        if (card.getImageUrl() != null) {
            String currentUrl = card.getImageUrl();
            validateImageUrl(currentUrl);
            boolean isPending = isPendingS3Url(currentUrl);

            S3ImageDownloader.DownloadResult result = s3ImageDownloader.download(currentUrl);
            String finalUrl = s3Uploader.upload(
                    S3Uploader.cardManualImageKey(id), result.bytes(), result.contentType());

            if (isPending) {
                tryDeleteS3(pendingKeyFromUrl(currentUrl));
            }
            card.updateImageUrl(finalUrl);
        }

        indexCard(card);
        String cardText = card.getName() + " " + card.getGrade() + " " + card.getSeries();
        eventPublisher.publishEvent(new CardEmbeddingEvent(card.getId(), cardText));
        return CardResponse.from(card);
    }

    public CardResponse rejectCard(Long id, String rejectReason) {
        Card card = cardRepository.findById(id)
                .orElseThrow(() -> new CardException(ErrorCode.CARD_NOT_FOUND));
        if (card.getImageUrl() != null && isPendingS3Url(card.getImageUrl())) {
            tryDeleteS3(pendingKeyFromUrl(card.getImageUrl()));
        }
        card.reject(rejectReason);
        return CardResponse.from(card);
    }

    void indexCard(Card card) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doIndexCard(card);
                }
            });
        } else {
            doIndexCard(card);
        }
    }

    private void doIndexCard(Card card) {
        try {
            Card freshCard = cardRepository.findByIdWithPokemon(card.getId()).orElse(null);
            if (freshCard == null) {
                log.warn("[ES_INDEXING] 카드 인덱싱 스킵 - 카드 없음 cardId={}", card.getId());
                return;
            }
            String nameKo    = freshCard.getPokemon() != null ? freshCard.getPokemon().getNameKo() : null;
            String seriesKo  = freshCard.getSeries() != null ? freshCard.getSeries().getNameKo() : null;
            String setNameKo = freshCard.getPokemonSet() != null ? freshCard.getPokemonSet().getNameKo() : null;
            cardSearchRepository.save(CardDocument.from(freshCard, nameKo, seriesKo, setNameKo));
        } catch (Exception e) {
            log.warn("[ES_INDEXING] 카드 인덱싱 실패 cardId={}: {}", card.getId(), e.getMessage());
        }
    }

    private void deleteCardIndex(Long id) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doDeleteCardIndex(id);
                }
            });
        } else {
            doDeleteCardIndex(id);
        }
    }

    private void doDeleteCardIndex(Long id) {
        try {
            cardSearchRepository.deleteById(String.valueOf(id));
        } catch (Exception e) {
            log.warn("[ES_INDEXING] 카드 인덱스 삭제 실패 cardId={}: {}", id, e.getMessage());
        }
    }

    private void validateImageUrl(String url) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                throw new CardException(ErrorCode.CARD_IMAGE_DOWNLOAD_FAILED);
            }
            String host = uri.getHost();
            if (host == null) {
                throw new CardException(ErrorCode.CARD_IMAGE_DOWNLOAD_FAILED);
            }
            // SSRF 방어: 내부망 / 루프백 / 링크로컬 차단
            if (host.equals("localhost")
                    || host.startsWith("127.")
                    || host.startsWith("10.")
                    || host.startsWith("192.168.")
                    || host.startsWith("169.254.")
                    || host.matches("172\\.(1[6-9]|2\\d|3[01])\\..*")) {
                throw new CardException(ErrorCode.CARD_IMAGE_DOWNLOAD_FAILED);
            }
        } catch (CardException e) {
            throw e;
        } catch (Exception e) {
            throw new CardException(ErrorCode.CARD_IMAGE_DOWNLOAD_FAILED);
        }
    }

    private boolean isPendingS3Url(String url) {
        return url.contains(".amazonaws.com/") && url.contains("/cards/pending/");
    }

    private String pendingKeyFromUrl(String url) {
        int idx = url.indexOf(".amazonaws.com/");
        if (idx == -1) {
            throw new IllegalArgumentException("S3 URL 형식이 아닙니다: " + url);
        }
        return url.substring(idx + ".amazonaws.com/".length());
    }

    private void tryDeleteS3(String key) {
        try {
            s3Uploader.delete(key);
        } catch (Exception e) {
            log.warn("[CardCommandService] S3 삭제 실패 key={}: {}", key, e.getMessage());
        }
    }
}
