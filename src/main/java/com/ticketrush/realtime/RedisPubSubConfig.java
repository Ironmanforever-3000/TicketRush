package com.ticketrush.realtime;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
@Profile("!worker")                              // REQUIRED: depends on SimpMessagingTemplate
@ConditionalOnProperty(name = "app.redis.listener.enabled", matchIfMissing = true)
public class RedisPubSubConfig {

    @Bean
    RedisMessageListenerContainer redisSeatListenerContainer(RedisConnectionFactory cf, RedisSeatSubscriber subscriber) {
        var container = new RedisMessageListenerContainer();
        container.setConnectionFactory(cf);
        container.addMessageListener(subscriber, new ChannelTopic("ticketrush:seat-updates"));
        return container;
    }
}
