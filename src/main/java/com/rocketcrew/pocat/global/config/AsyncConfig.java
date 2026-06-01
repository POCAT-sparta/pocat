package com.rocketcrew.pocat.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    /**
     * TCGdex 동기화 전용 스레드 풀.
     * 동기화는 장시간 실행될 수 있으므로 별도 스레드 풀로 분리한다.
     * - corePoolSize 1: 동시에 1개의 동기화 작업만 실행
     * - queueCapacity 1: 실행 중 추가 요청이 들어오면 최대 1개까지 대기
     */
    @Bean(name = "syncExecutor")
    public Executor syncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.setThreadNamePrefix("card-sync-");
        executor.initialize();
        return executor;
    }

    /**
     * 어드민 AI 작업 전용 스레드 풀.
     * RAG 재색인 등 장시간 실행 어드민 작업에 사용한다.
     * - corePoolSize 1: AtomicBoolean 중복 방지와 일관성 유지
     * - queueCapacity 1: 재색인 중 추가 요청 최대 1개 대기
     */
    @Bean(name = "adminTaskExecutor")
    public Executor adminTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.setThreadNamePrefix("ai-admin-");
        executor.initialize();
        return executor;
    }
}
