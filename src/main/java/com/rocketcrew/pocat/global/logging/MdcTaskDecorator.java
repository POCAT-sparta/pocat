package com.rocketcrew.pocat.global.logging;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

/**
 * @Async 스레드 풀에서 HTTP 요청 스레드의 MDC 컨텍스트(requestId, userId 등)를
 * 자식 스레드로 전파한다.
 * AsyncConfig의 각 executor에 setTaskDecorator()로 등록해 사용한다.
 */
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        return () -> {
            try {
                if (contextMap != null) {
                    MDC.setContextMap(contextMap);
                }
                runnable.run();
            } finally {
                MDC.clear();
            }
        };
    }
}
