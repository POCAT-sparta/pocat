package com.rocketcrew.pocat.domain.auction.repository;

import com.rocketcrew.pocat.domain.auction.document.AuctionDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface AuctionSearchRepository extends ElasticsearchRepository<AuctionDocument, String> {
}
