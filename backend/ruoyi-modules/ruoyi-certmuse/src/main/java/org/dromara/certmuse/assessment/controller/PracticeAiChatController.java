package org.dromara.certmuse.assessment.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.dromara.certmuse.assessment.domain.bo.SendPracticeAiMessageBo;
import org.dromara.certmuse.assessment.domain.vo.CancelPracticeAiMessageVo;
import org.dromara.certmuse.assessment.domain.vo.CreatePracticeAiConversationVo;
import org.dromara.certmuse.assessment.domain.vo.PracticeAiConversationVo;
import org.dromara.certmuse.assessment.domain.vo.PracticeAiMessagePageVo;
import org.dromara.certmuse.assessment.service.PracticeAiChatService;
import org.dromara.common.core.domain.R;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Learner endpoints for persistent question-scoped AI conversations. */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/assessment")
@SaCheckPermission(value = {"certmuse:student", "certmuse:assessment:knowledge-practice:ai-chat"}, mode = SaMode.AND)
public class PracticeAiChatController {
    private static final String UUID = "^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$";
    private final PracticeAiChatService service;

    @PostMapping("/knowledge-practices/{sessionId}/items/{questionOrder}/ai-conversations")
    public ResponseEntity<R<CreatePracticeAiConversationVo>> create(
        @PathVariable @Min(1) long sessionId, @PathVariable @Min(1) int questionOrder,
        @RequestHeader("X-Request-Id") @Pattern(regexp = UUID) String requestId) {
        CreatePracticeAiConversationVo result = service.create(LoginHelper.getUserId(), sessionId, questionOrder, requestId);
        R<CreatePracticeAiConversationVo> body = new R<>();
        int status = result.created() ? 201 : 200;
        body.setCode(status);
        body.setMsg(result.created() ? "创建成功" : "操作成功");
        body.setData(result);
        return ResponseEntity.status(status).body(body);
    }

    @GetMapping("/ai-conversations/{conversationId}")
    public R<PracticeAiConversationVo> conversation(@PathVariable @Min(1) long conversationId) {
        return R.ok(service.conversation(LoginHelper.getUserId(), conversationId));
    }

    @GetMapping("/ai-conversations/{conversationId}/messages")
    public R<PracticeAiMessagePageVo> messages(
        @PathVariable @Min(1) long conversationId,
        @RequestParam(required = false) @Min(1) Integer beforeSequence,
        @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit) {
        return R.ok(service.messages(LoginHelper.getUserId(), conversationId, beforeSequence, limit));
    }

    @PostMapping(value = "/ai-conversations/{conversationId}/messages/stream",
        produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> stream(
        @PathVariable @Min(1) long conversationId,
        @RequestHeader("X-Request-Id") @Pattern(regexp = UUID) String requestId,
        @Valid @RequestBody SendPracticeAiMessageBo command) {
        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_EVENT_STREAM)
            .header("Cache-Control", "no-cache, no-transform")
            .header("X-Accel-Buffering", "no")
            .header("X-Request-Id", requestId)
            .body(service.stream(LoginHelper.getUserId(), conversationId, requestId, command));
    }

    @PostMapping("/ai-conversations/{conversationId}/messages/{assistantMessageId}/cancel")
    public R<CancelPracticeAiMessageVo> cancel(
        @PathVariable @Min(1) long conversationId, @PathVariable @Min(1) long assistantMessageId,
        @RequestHeader("X-Request-Id") @Pattern(regexp = UUID) String requestId) {
        return R.ok(service.cancel(LoginHelper.getUserId(), conversationId, assistantMessageId, requestId));
    }
}
