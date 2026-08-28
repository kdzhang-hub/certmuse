package org.dromara.common.push.core;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.map.MapUtil;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.utils.ThreadUtils;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.push.constant.MessageConstants;
import org.dromara.common.push.dto.PushDTO;
import org.dromara.common.push.metrics.SseMetrics;
import org.dromara.common.push.properties.MessageProperties;
import org.dromara.common.redis.utils.RedisUtils;
import org.dromara.system.api.domain.PushPayloadDTO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 管理 Server-Sent Events (SSE) 连接
 *
 * @author Lion Li
 */
@Slf4j
public class SseEmitterSessionManager implements PushSessionManager {

    private final static Map<Long, Map<String, SseEmitter>> USER_TOKEN_EMITTERS = new ConcurrentHashMap<>();
    private final static Map<Long, ConcurrentLinkedDeque<String>> USER_CONNECTION_ORDER = new ConcurrentHashMap<>();

    private final MessageProperties messageProperties;
    private final SseMetrics metrics;

    /**
     * 构造 SSE 会话管理器并启动心跳检测。
     *
     * @param scheduledExecutorService 定时任务线程池
     * @param messageProperties        消息推送配置
     */
    public SseEmitterSessionManager(ScheduledExecutorService scheduledExecutorService, MessageProperties messageProperties,
                                    SseMetrics metrics) {
        this.messageProperties = messageProperties;
        this.metrics = metrics;
        // 定时执行 SSE 心跳检测
        scheduledExecutorService.scheduleWithFixedDelay(
            this::sseMonitor,
            messageProperties.getHeartbeatInterval(),
            messageProperties.getHeartbeatInterval(),
            TimeUnit.SECONDS
        );
    }

    /**
     * 建立与指定用户的 SSE 连接
     *
     * @param userId 用户的唯一标识符，用于区分不同用户的连接
     * @param token  用户的唯一令牌，用于识别具体的连接
     * @return 返回一个 SseEmitter 实例，客户端可以通过该实例接收 SSE 事件
     */
    public SseEmitter connect(Long userId, String connectionId) {
        // 从 USER_TOKEN_EMITTERS 中获取或创建当前用户的 SseEmitter 映射表（ConcurrentHashMap）
        // 每个用户可以有多个 SSE 连接，通过 token 进行区分
        Map<String, SseEmitter> emitters = USER_TOKEN_EMITTERS.computeIfAbsent(userId, k -> new ConcurrentHashMap<>());

        // 关闭已存在的SseEmitter，防止超过最大连接数
        SseEmitter oldEmitter = emitters.remove(connectionId);
        if (oldEmitter != null) {
            metrics.connectionClosed();
            sendKickedMessage(oldEmitter);
            oldEmitter.complete();
        }

        // 创建一个新的 SseEmitter 实例，避免连接之后直接关闭浏览器导致连接停滞
        SseEmitter emitter = new SseEmitter(messageProperties.getSseTimeout());

        emitters.put(connectionId, emitter);
        metrics.connectionOpened();
        ConcurrentLinkedDeque<String> order = USER_CONNECTION_ORDER.computeIfAbsent(
            userId, ignored -> new ConcurrentLinkedDeque<>()
        );
        order.remove(connectionId);
        order.addLast(connectionId);
        while (order.size() > messageProperties.getMaxConnectionsPerUser()) {
            String oldest = order.pollFirst();
            if (oldest != null) {
                SseEmitter evicted = emitters.remove(oldest);
                if (evicted != null) {
                    metrics.connectionClosed();
                    sendKickedMessage(evicted);
                    evicted.complete();
                }
            }
        }

        // 当 emitter 完成、超时或发生错误时，从映射表中移除对应的 token
        emitter.onCompletion(() -> {
            SseEmitter remove = removeConnection(userId, connectionId);
            if (remove != null) {
                remove.complete();
            }
        });
        emitter.onTimeout(() -> {
            SseEmitter remove = removeConnection(userId, connectionId);
            if (remove != null) {
                remove.complete();
            }
        });
        emitter.onError((e) -> {
            SseEmitter remove = removeConnection(userId, connectionId);
            if (remove != null) {
                remove.complete();
            }
        });

        try {
            // 向客户端发送一条连接成功的事件
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException e) {
            // 如果发送消息失败，则从映射表中移除 emitter
            removeConnection(userId, connectionId);
        }
        return emitter;
    }

    /**
     * 通知旧连接已被同 token 新连接替换。
     *
     * @param emitter 旧 SSE 连接
     */
    private void sendKickedMessage(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event()
                .name("message")
                .data(MessageConstants.KICKED));
        } catch (Exception ignore) {
            // 旧连接可能已断开，忽略通知失败
        }
    }

    /**
     * 断开指定用户的 SSE 连接
     *
     * @param userId 用户的唯一标识符，用于区分不同用户的连接
     * @param token  用户的唯一令牌，用于识别具体的连接
     */
    public void disconnect(Long userId, String connectionId) {
        if (userId == null || connectionId == null) {
            return;
        }
        Map<String, SseEmitter> emitters = USER_TOKEN_EMITTERS.get(userId);
        if (MapUtil.isNotEmpty(emitters)) {
            try {
                SseEmitter sseEmitter = emitters.get(connectionId);
                if (sseEmitter != null) {
                    sseEmitter.send(SseEmitter.event().comment("disconnected"));
                    sseEmitter.complete();
                }
            } catch (Exception ignore) {
            }
            removeConnection(userId, connectionId);
        } else {
            USER_TOKEN_EMITTERS.remove(userId);
        }
    }

    /**
     * Completes all local SSE connections during application shutdown.
     */
    public void closeAll() {
        int connectionCount = currentConnectionCount();
        USER_TOKEN_EMITTERS.values().forEach(emitters -> emitters.values().forEach(emitter -> {
            try {
                emitter.complete();
            } catch (Exception ignore) {
                // Connection may already be closed.
            }
        }));
        USER_TOKEN_EMITTERS.clear();
        USER_CONNECTION_ORDER.clear();
        for (int index = 0; index < connectionCount; index++) {
            metrics.connectionClosed();
        }
    }

    /**
     * Returns the number of local connections for a user.
     */
    public int currentConnectionCount(Long userId) {
        Map<String, SseEmitter> emitters = USER_TOKEN_EMITTERS.get(userId);
        return emitters == null ? 0 : emitters.size();
    }

    /**
     * Returns the total number of local SSE connections.
     */
    public int currentConnectionCount() {
        return USER_TOKEN_EMITTERS.values().stream().mapToInt(Map::size).sum();
    }

    /**
     * 执行 SSE 心跳检测并清理失效连接。
     */
    public void sseMonitor() {
        final SseEmitter.SseEventBuilder heartbeat = SseEmitter.event().comment("heartbeat");
        // 记录需要移除的用户ID
        List<Long> toRemoveUsers = new ArrayList<>();

        USER_TOKEN_EMITTERS.forEach((userId, emitterMap) -> {
            if (CollUtil.isEmpty(emitterMap)) {
                toRemoveUsers.add(userId);
                USER_CONNECTION_ORDER.remove(userId);
                return;
            }

            emitterMap.entrySet().removeIf(entry -> {
                try {
                    entry.getValue().send(heartbeat);
                    return false;
                } catch (Exception ex) {
                    metrics.heartbeatFailed();
                    try {
                        entry.getValue().complete();
                    } catch (Exception ignore) {
                        // 忽略重复关闭异常
                    }
                    ConcurrentLinkedDeque<String> order = USER_CONNECTION_ORDER.get(userId);
                    if (order != null) {
                        order.remove(entry.getKey());
                    }
                    return true; // 发送失败 → 移除该连接
                }
            });

            // 移除空连接用户
            if (emitterMap.isEmpty()) {
                toRemoveUsers.add(userId);
                USER_CONNECTION_ORDER.remove(userId);
            }
        });

        // 循环结束后统一清理空用户，避免并发修改异常
        toRemoveUsers.forEach(USER_TOKEN_EMITTERS::remove);
    }

    /**
     * 订阅 SSE 广播主题消息。
     *
     * @param consumer 处理SSE消息的消费者函数
     */
    @Override
    public void subscribeMessage(Consumer<PushDTO> consumer) {
        RedisUtils.subscribe(MessageConstants.MESSAGE_TOPIC, PushDTO.class, consumer);
    }

    /**
     * 向指定用户的全部本地 SSE 会话发送消息。
     *
     * @param userId  要发送消息的用户id
     * @param message 要发送的消息内容
     */
    public void sendMessage(Long userId, String message) {
        Map<String, SseEmitter> emitters = USER_TOKEN_EMITTERS.get(userId);
        if (MapUtil.isNotEmpty(emitters)) {
            for (Map.Entry<String, SseEmitter> entry : emitters.entrySet()) {
                try {
                    entry.getValue().send(SseEmitter.event()
                        .name("message")
                        .data(message));
                    metrics.deliverySucceeded();
                } catch (Exception e) {
                    metrics.deliveryFailed();
                    SseEmitter remove = removeConnection(userId, entry.getKey());
                    if (remove != null) {
                        remove.complete();
                    }
                }
            }
        } else {
            USER_TOKEN_EMITTERS.remove(userId);
        }
    }

    /**
     * 向指定用户的全部本地 SSE 会话发送统一 JSON 消息。
     *
     * @param userId  要发送消息的用户id
     * @param payload 要发送的消息体
     */
    @Override
    public void sendMessage(Long userId, PushPayloadDTO payload) {
        if (payload == null) {
            return;
        }
        sendMessage(userId, JsonUtils.toJsonString(payload));
    }

    /**
     * 向指定用户的全部本地 SSE 会话发送统一 JSON 消息。
     *
     * @param userId  要发送消息的用户id
     * @param pushDTO 要发送的消息内容
     */
    public void sendMessage(Long userId, PushDTO pushDTO) {
        if (pushDTO == null) {
            return;
        }
        sendMessage(userId, pushDTO.getPayload());
    }

    /**
     * 向当前节点所有 SSE 会话发送消息。
     *
     * @param message 要发送的消息内容
     */
    public void sendMessage(String message) {
        List<Long> userIds = new ArrayList<>(USER_TOKEN_EMITTERS.keySet());
        Runnable[] sendTasks = userIds.stream()
            .map(userId -> (Runnable) () -> sendMessage(userId, message))
            .toArray(Runnable[]::new);
        ThreadUtils.virtualInvokeAll(sendTasks);
    }

    /**
     * 向当前节点所有 SSE 会话发送统一 JSON 消息。
     *
     * @param payload 要发送的消息体
     */
    @Override
    public void sendMessage(PushPayloadDTO payload) {
        if (payload == null) {
            return;
        }
        sendMessage(JsonUtils.toJsonString(payload));
    }

    /**
     * 发布 SSE 订阅消息。
     *
     * @param pushDTO 要发布的SSE消息对象
     */
    @Override
    public void publishMessage(PushDTO pushDTO) {
        if (pushDTO == null || pushDTO.getPayload() == null) {
            return;
        }
        RedisUtils.publish(MessageConstants.MESSAGE_TOPIC, pushDTO, consumer -> log.info(
            "发送主题订阅消息 topic={}, payloadType={}, targetCount={}",
            MessageConstants.MESSAGE_TOPIC,
            pushDTO.getPayload().getType(),
            pushDTO.getUserIds() == null ? 0 : pushDTO.getUserIds().size()
        ));
    }

    /**
     * 发布 SSE 广播消息。
     *
     * @param message 要发布的消息内容
     */
    public void publishAll(String message) {
        publishAll(PushPayloadDTO.of("message", "backend", message, null));
    }

    /**
     * 发布 SSE 广播 JSON 消息。
     *
     * @param payload 要发布的消息体
     */
    @Override
    public void publishAll(PushPayloadDTO payload) {
        publishMessage(PushDTO.broadcast(payload));
    }

    private SseEmitter removeConnection(Long userId, String connectionId) {
        Map<String, SseEmitter> emitters = USER_TOKEN_EMITTERS.get(userId);
        SseEmitter removed = emitters == null ? null : emitters.remove(connectionId);
        ConcurrentLinkedDeque<String> order = USER_CONNECTION_ORDER.get(userId);
        if (order != null) {
            order.remove(connectionId);
            if (order.isEmpty()) {
                USER_CONNECTION_ORDER.remove(userId, order);
            }
        }
        if (emitters != null && emitters.isEmpty()) {
            USER_TOKEN_EMITTERS.remove(userId, emitters);
        }
        if (removed != null) {
            metrics.connectionClosed();
        }
        return removed;
    }
}
