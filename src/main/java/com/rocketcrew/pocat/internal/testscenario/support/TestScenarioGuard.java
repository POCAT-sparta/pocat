package com.rocketcrew.pocat.internal.testscenario.support;

import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.common.ServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TestScenarioGuard {

    @Value("${pocat.test-scenarios.enabled:false}")
    private boolean enabled;

    public void ensureEnabled() {
        if (!enabled) {
            throw new ServiceException(ErrorCode.TEST_SCENARIO_DISABLED);
        }
    }
}
