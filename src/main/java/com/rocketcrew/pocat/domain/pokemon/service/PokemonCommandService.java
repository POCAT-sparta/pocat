package com.rocketcrew.pocat.domain.pokemon.service;

import com.rocketcrew.pocat.domain.pokemon.dto.response.PokemonResponse;
import com.rocketcrew.pocat.domain.pokemon.entity.Pokemon;
import com.rocketcrew.pocat.domain.pokemon.repository.PokemonRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.PokemonException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PokemonCommandService {

    private final PokemonRepository pokemonRepository;

    /** 정규화된 영문 포켓몬명 → Pokemon 인메모리 캐시 */
    private final Map<String, Pokemon> nameCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void buildCache() {
        pokemonRepository.findAll().forEach(p -> nameCache.put(normalize(p.getName()), p));
        log.info("[PokemonCache] {}개 포켓몬 캐시 로드", nameCache.size());
    }

    /**
     * 카드명("Charizard ex")에서 슬라이딩 윈도우로 포켓몬명 추출.
     * 긴 구간(다단어)부터 먼저 시도 → Mr. Mime, Tapu Koko 등 대응.
     */
    public Optional<Pokemon> findOrCreateForCardName(String cardName) {
        if (cardName == null) return Optional.empty();
        String[] words = cardName.split("\\s+");
        for (int len = words.length; len >= 1; len--) {
            for (int start = 0; start <= words.length - len; start++) {
                String candidate = String.join("", Arrays.copyOfRange(words, start, start + len));
                Pokemon cached = nameCache.get(normalize(candidate));
                if (cached != null) return Optional.of(cached);
            }
        }
        return Optional.empty();
    }

    /** pokemon-names.yml 에서 읽어온 (nameEn, nameKo) 쌍으로 일괄 저장 */
    public Pokemon findOrCreate(String name, String nameKo) {
        return pokemonRepository.findByName(name).orElseGet(() -> {
            Pokemon saved = pokemonRepository.save(
                    Pokemon.builder().name(name).nameKo(nameKo).build());
            nameCache.put(normalize(name), saved);
            return saved;
        });
    }

    public PokemonResponse updateNameKo(Long id, String nameKo) {
        Pokemon pokemon = pokemonRepository.findById(id)
                .orElseThrow(() -> new PokemonException(ErrorCode.POKEMON_NOT_FOUND));
        pokemon.updateNameKo(nameKo);
        nameCache.put(normalize(pokemon.getName()), pokemon);
        return PokemonResponse.from(pokemon);
    }

    public void delete(Long id) {
        Pokemon pokemon = pokemonRepository.findById(id)
                .orElseThrow(() -> new PokemonException(ErrorCode.POKEMON_NOT_FOUND));
        nameCache.remove(normalize(pokemon.getName()));
        pokemonRepository.delete(pokemon);
    }

    public int getCacheSize() {
        return nameCache.size();
    }

    private static String normalize(String word) {
        return word.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
