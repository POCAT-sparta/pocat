package com.rocketcrew.pocat.domain.payment.event;

public final class PaymentEventType {

    public static final String COMPLETED     = "payment.completed";
    public static final String AUTO_FAILED   = "payment.auto.failed";
    public static final String DIRECT_FAILED = "payment.direct.failed";

    private PaymentEventType() {
    }
}
