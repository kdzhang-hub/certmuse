package org.dromara.common.push.config;

import org.dromara.common.push.annotation.ConditionalOnMessageTransport;
import org.dromara.common.push.controller.SseController;
import org.dromara.common.push.core.SseEmitterSessionManager;
import org.dromara.common.push.listener.MessageTopicListener;
import org.dromara.common.push.metrics.SseMetrics;
import org.dromara.common.push.properties.MessageProperties;
import org.dromara.common.push.ticket.MessageTicketService;
import org.dromara.common.push.ticket.MessageTicketStore;
import org.dromara.common.push.ticket.RedisMessageTicketStore;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import io.micrometer.core.instrument.MeterRegistry;
import tools.jackson.databind.json.JsonMapper;

import java.util.concurrent.ScheduledExecutorService;

/**
 * SSE 消息推送自动装配。
 *
 * @author Lion Li
 */
@AutoConfiguration(after = MessageAutoConfiguration.class)
@ConditionalOnMessageTransport("sse")
public class MessageSseConfiguration {

    /**
     * 注册 SSE 会话管理器
     * 负责管理用户 SSE 连接、消息发送、会话清理
     *
     * @return SseEmitterSessionManager 实例
     */
    @Bean
    public SseEmitterSessionManager sseEmitterManager(ScheduledExecutorService scheduledExecutorService,
                                                      MessageProperties messageProperties, SseMetrics metrics) {
        return new SseEmitterSessionManager(scheduledExecutorService, messageProperties, metrics);
    }

    @Bean
    public SseMetrics sseMetrics(MeterRegistry meterRegistry) {
        return new SseMetrics(meterRegistry);
    }

    @Bean
    public MessageTicketStore messageTicketStore(RedissonClient redissonClient, JsonMapper jsonMapper) {
        return new RedisMessageTicketStore(redissonClient, jsonMapper);
    }

    @Bean
    public MessageTicketService messageTicketService(MessageTicketStore store, MessageProperties properties,
                                                     SseMetrics metrics) {
        return new MessageTicketService(store, properties, metrics);
    }

    /**
     * 注册消息主题监听器
     * 监听 Redis 全局消息，用于集群环境下的消息分发
     *
     * @param manager SSE 会话管理器
     * @return MessageTopicListener 实例
     */
    @Bean
    public MessageTopicListener messageTopicListener(SseEmitterSessionManager manager) {
        return new MessageTopicListener(manager);
    }

    /**
     * 注册 SSE 控制器
     * 提供前端建立 SSE 连接的接口
     *
     * @param manager SSE 会话管理器
     * @return SseController 实例
     */
    @Bean
    public SseController sseController(SseEmitterSessionManager manager, MessageTicketService ticketService) {
        return new SseController(manager, ticketService);
    }
}
