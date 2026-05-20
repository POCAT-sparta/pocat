package com.rocketcrew.pocat.global.util;

import io.hypersistence.tsid.TSID;

public class TsidGenerator {

    private TsidGenerator() {}

    public static String generateOrderUid() {
        return "ORD-" + TSID.fast();
    }

    public static String generatePaymentUid() {
        return "PAY-" + TSID.fast();
    }

    public static String generateRefundUid() {
        return "REF-" + TSID.fast();
    }

    public static String generateSettlementUid() {
        return "SLT-" + TSID.fast();
    }
}
