package com.rocketcrew.pocat.global.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class PaymentMetrics {

    private final MeterRegistry registry;

    private final Counter autoAttempt;
    private final Counter autoSuccess;
    private final Counter autoFail;
    private final Counter directAttempt;
    private final Counter directSuccess;
    private final Counter directFail;
    private final Counter webhookAttempt;
    private final Counter webhookSuccess;
    private final Counter webhookUserCancel;
    private final Counter webhookFail;
    private final Counter amountMismatch;
    private final Timer   autoPaymentTimer;
    private final Timer   directPaymentTimer;

    public PaymentMetrics(MeterRegistry registry) {
        this.registry = registry;

        autoAttempt       = Counter.builder("payment.auto.attempt.total").register(registry);
        autoSuccess       = Counter.builder("payment.auto.success.total").register(registry);
        autoFail          = Counter.builder("payment.auto.fail.total").register(registry);
        directAttempt     = Counter.builder("payment.direct.attempt.total").register(registry);
        directSuccess     = Counter.builder("payment.direct.success.total").register(registry);
        directFail        = Counter.builder("payment.direct.fail.total").register(registry);
        webhookAttempt    = Counter.builder("payment.webhook.attempt.total").register(registry);
        webhookSuccess    = Counter.builder("payment.webhook.success.total").register(registry);
        webhookUserCancel = Counter.builder("payment.webhook.user_cancel.total").register(registry);
        webhookFail       = Counter.builder("payment.webhook.fail.total").register(registry);
        amountMismatch    = Counter.builder("payment.amount_mismatch.total").register(registry);

        autoPaymentTimer   = Timer.builder("payment.duration")
                .description("결제 처리 소요 시간")
                .tag("type", "auto")
                .register(registry);
        directPaymentTimer = Timer.builder("payment.duration")
                .description("결제 처리 소요 시간")
                .tag("type", "direct")
                .register(registry);
    }

    public void incrementAutoAttempt()       { autoAttempt.increment(); }
    public void incrementAutoSuccess()       { autoSuccess.increment(); }
    public void incrementAutoFail()          { autoFail.increment(); }
    public void incrementDirectAttempt()     { directAttempt.increment(); }
    public void incrementDirectSuccess()     { directSuccess.increment(); }
    public void incrementDirectFail()        { directFail.increment(); }
    public void incrementWebhookAttempt()    { webhookAttempt.increment(); }
    public void incrementWebhookSuccess()    { webhookSuccess.increment(); }
    public void incrementWebhookUserCancel() { webhookUserCancel.increment(); }
    public void incrementWebhookFail()       { webhookFail.increment(); }
    public void incrementAmountMismatch()    { amountMismatch.increment(); }

    public Timer.Sample startTimer()                         { return Timer.start(registry); }
    public void recordAutoPaymentDuration(Timer.Sample s)   { s.stop(autoPaymentTimer); }
    public void recordDirectPaymentDuration(Timer.Sample s) { s.stop(directPaymentTimer); }
}
