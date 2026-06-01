package com.rocketcrew.pocat.domain.auction.event;

public final class AuctionEventType {

    public static final String INSPECTION_PASSED = "auction.inspection.passed";
    public static final String INSPECTION_FAILED = "auction.inspection.failed";
    public static final String ACTIVATED = "auction.activated";
    public static final String CANCELLED = "auction.cancelled";
    public static final String ENDED = "auction.ended";
    public static final String BUYOUT_COMPLETED = "auction.buyout.completed";

    private AuctionEventType() {
    }
}
