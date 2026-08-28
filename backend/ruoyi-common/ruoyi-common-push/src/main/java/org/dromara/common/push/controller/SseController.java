package org.dromara.common.push.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.push.annotation.ConditionalOnMessageTransport;
import org.dromara.common.push.core.SseEmitterSessionManager;
import org.dromara.common.push.ticket.MessageTicket;
import org.dromara.common.push.ticket.MessageTicketPrincipal;
import org.dromara.common.push.ticket.MessageTicketService;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

/**
 * SSE 控制器
 *
 * @author Lion Li
 */
@RestController
@ConditionalOnMessageTransport("sse")
@RequiredArgsConstructor
public class SseController implements DisposableBean {

    private final SseEmitterSessionManager sessionManager;
    private final MessageTicketService ticketService;

    @PostMapping("${message.path:/resource/message}/ticket")
    @SaCheckLogin
    public R<MessageTicket> ticket(@RequestHeader("clientid") String clientId) {
        StpUtil.checkLogin();
        Object authenticatedClientId = StpUtil.getExtra(LoginHelper.CLIENT_KEY);
        if (!clientId.equals(authenticatedClientId)) {
            throw new IllegalArgumentException("SSE ticket client identity does not match login session");
        }
        return R.ok(ticketService.issue(LoginHelper.getUserId(), clientId));
    }

    /**
     * 建立当前登录用户的 SSE 连接。
     *
     * @return SSE 发射器
     */
    @GetMapping(value = "${message.path:/resource/message}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @SaIgnore
    public SseEmitter connect(@RequestParam(required = false) String ticket, HttpServletResponse response) throws IOException {
        MessageTicketPrincipal principal = ticketService.consume(ticket).orElse(null);
        if (principal == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "SSE ticket is invalid or expired");
            return null;
        }
        prepareSseResponse(response);
        return sessionManager.connect(principal.userId(), principal.connectionId());
    }

    /**
     * 关闭当前登录用户的 SSE 连接。
     *
     * @return 操作结果
     */
    @PostMapping(value = "${message.path:/resource/message}/close")
    @SaCheckLogin
    public R<Void> close(@RequestParam String connectionId) {
        StpUtil.checkLogin();
        if (StrUtil.isBlank(connectionId)) {
            throw new IllegalArgumentException("connectionId is required");
        }
        Long userId = LoginHelper.getUserId();
        sessionManager.disconnect(userId, connectionId);
        return R.ok();
    }

    /**
     * 设置 SSE 响应头，覆盖统一鉴权成功路径中的默认 JSON 响应类型。
     *
     * @param response 当前响应
     */
    private void prepareSseResponse(HttpServletResponse response) {
        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
    }

    // 以下为demo仅供参考 禁止使用 请在业务逻辑中使用工具发送而不是用接口发送
//    /**
//     * 向特定用户发送消息
//     *
//     * @param userId 目标用户的 ID
//     * @param msg    要发送的消息内容
//     */
//    @GetMapping(value = "${message.path:/resource/message}/send")
//    public R<Void> send(Long userId, String msg) {
//        PushDTO dto = new PushDTO();
//        dto.setUserIds(List.of(userId));
//        dto.setPayload(PushPayloadDTO.of("message", "backend", msg, null));
//        sessionManager.publishMessage(dto);
//        return R.ok();
//    }
//
//    /**
//     * 向所有用户发送消息
//     *
//     * @param msg 要发送的消息内容
//     */
//    @GetMapping(value = "${message.path:/resource/message}/sendAll")
//    public R<Void> send(String msg) {
//        sessionManager.publishAll(msg);
//        return R.ok();
//    }

    /**
     * 容器销毁时释放资源占位实现。
     *
     * @throws Exception 销毁异常
     */
    @Override
    public void destroy() throws Exception {
        sessionManager.closeAll();
    }

}
