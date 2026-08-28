package org.dromara.common.push.properties;

import lombok.Data;
import org.dromara.common.push.enums.MessageTransportEnum;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 统一消息推送配置。
 *
 * @author Lion Li
 */
@Data
@ConfigurationProperties("message")
public class MessageProperties {

    /**
     * 是否启用消息推送。
     */
    private Boolean enabled = true;

    /**
     * 传输方式：sse / websocket。
     */
    private String transport = MessageTransportEnum.SSE.getCode();

    /**
     * 统一访问路径。
     */
    private String path = "/resource/message";

    /**
     * WebSocket 允许的跨域来源。
     */
    private String[] allowedOrigins = {"*"};

    /**
     * SSE 连接超时时间，单位毫秒。
     */
    private long sseTimeout = 86_400_000L;

    /**
     * 本地连接心跳检测间隔，单位秒。
     */
    private long heartbeatInterval = 60L;

    /**
     * SSE 一次性连接票据有效期。
     */
    private Duration ticketTtl = Duration.ofSeconds(60);

    /**
     * 单用户允许的最大并发 SSE 连接数。
     */
    private int maxConnectionsPerUser = 2;

    /**
     * WebSocket 单次发送超时时间，单位毫秒。
     */
    private int webSocketSendTimeLimit = 10_000;

    /**
     * WebSocket 发送缓冲区大小。
     */
    private int webSocketBufferSizeLimit = 64_000;
}
