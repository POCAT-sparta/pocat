package com.rocketcrew.pocat.global.config;

import com.rocketcrew.pocat.domain.order.service.ExpiryEventListener;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import com.rocketcrew.pocat.domain.auction.redis.AuctionExpirationRedisSubscriber;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.util.StringUtils;

@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.cluster.nodes}")
    private List<String> clusterNodes;

    @Value("${spring.data.redis.password:}")
    private String password;

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisClusterConfiguration clusterConfig = new RedisClusterConfiguration(clusterNodes);
        clusterConfig.setMaxRedirects(3);
        if (StringUtils.hasText(password)) {
            clusterConfig.setPassword(RedisPassword.of(password));
        }

        ClusterTopologyRefreshOptions refreshOptions = ClusterTopologyRefreshOptions.builder()
                .enablePeriodicRefresh(Duration.ofMinutes(1))
                .enableAllAdaptiveRefreshTriggers()
                .build();

        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .clientOptions(ClusterClientOptions.builder()
                        .topologyRefreshOptions(refreshOptions)
                        .build())
                .build();

        return new LettuceConnectionFactory(clusterConfig, clientConfig);
    }

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        var clusterServersConfig = config.useClusterServers()
                .addNodeAddress(
                        clusterNodes.stream()
                                .map(node -> "redis://" + node)
                                .toArray(String[]::new)
                );
        if (StringUtils.hasText(password)) {
            clusterServersConfig.setPassword(password);
        }
        return Redisson.create(config);
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        return template;
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            ExpiryEventListener ExpiryEventListener,
            AuctionExpirationRedisSubscriber auctionExpirationRedisSubscriber) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(ExpiryEventListener, new PatternTopic("__keyevent@*__:expired"));
        container.addMessageListener(auctionExpirationRedisSubscriber, new PatternTopic("__keyevent@*__:expired"));
        return container;
    }

}
