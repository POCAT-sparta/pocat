package com.rocketcrew.pocat.support;

import com.rocketcrew.pocat.domain.auction.repository.AuctionSearchRepository;
import com.rocketcrew.pocat.domain.card.repository.CardSearchRepository;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;

@TestConfiguration
public class MockElasticsearchTestConfig {

    @Bean
    @Primary
    public ElasticsearchOperations elasticsearchOperations() {
        return Mockito.mock(ElasticsearchOperations.class);
    }

    @Bean
    @Primary
    public AuctionSearchRepository auctionSearchRepository() {
        return Mockito.mock(AuctionSearchRepository.class);
    }

    @Bean
    @Primary
    public CardSearchRepository cardSearchRepository() {
        return Mockito.mock(CardSearchRepository.class);
    }
}
