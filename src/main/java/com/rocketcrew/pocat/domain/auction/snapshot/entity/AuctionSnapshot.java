package com.rocketcrew.pocat.domain.auction.snapshot.entity;

import com.rocketcrew.pocat.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
@Entity
@Table(
        name = "auction_snapshots",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_auction_snapshots_auction_id",
                columnNames = "auction_id"
        )
)
@SQLDelete(sql = "UPDATE auction_snapshots SET deleted_at = NOW() WHERE id = ?")
public class AuctionSnapshot extends BaseEntity {

    @Column(nullable = false)
    private Long auctionId;

    @Column
    private Long finalPrice;

    @Column(columnDefinition = "TEXT")
    private String snapshotJson;
}
