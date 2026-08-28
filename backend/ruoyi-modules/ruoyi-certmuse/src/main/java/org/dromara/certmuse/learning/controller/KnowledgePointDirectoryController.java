package org.dromara.certmuse.learning.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.dromara.certmuse.learning.domain.vo.KnowledgePointDirectoryErrorVo;
import org.dromara.certmuse.learning.domain.vo.KnowledgePointDirectoryVo;
import org.dromara.certmuse.learning.service.KnowledgePointDirectoryService;
import org.dromara.certmuse.learning.support.KnowledgePointDirectoryException;
import org.dromara.common.core.domain.R;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** U07 learner-only knowledge-point directory mapping endpoint. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/learning/knowledge-points")
public class KnowledgePointDirectoryController {
    private final KnowledgePointDirectoryService service;

    /** Resolves current-goal visible labels for a comma-separated batch of knowledge-point IDs. */
    @SaCheckPermission(value = {"certmuse:student", "certmuse:learning:knowledge-point:query"}, mode = SaMode.AND)
    @GetMapping
    public R<KnowledgePointDirectoryVo> lookup(HttpServletRequest request) {
        String ids = rejectUnsupportedRequestShape(request);
        return R.ok(service.lookup(LoginHelper.getUserId(), ids));
    }

    private String rejectUnsupportedRequestShape(HttpServletRequest request) {
        var parameters = request.getParameterMap();
        if (!parameters.containsKey("ids")) {
            if (parameters.isEmpty()) {
                throw invalid(List.of(new KnowledgePointDirectoryErrorVo.FieldErrorVo(
                    "ids", "REQUIRED", "ids不能为空")));
            }
            throw invalid(List.of());
        }
        if (parameters.size() != 1) {
            throw invalid(List.of());
        }
        String[] ids = parameters.get("ids");
        if (ids == null || ids.length != 1) {
            throw invalid(List.of(new KnowledgePointDirectoryErrorVo.FieldErrorVo(
                "ids", "INVALID_FORMAT", "ids只能出现一次")));
        }
        return ids[0];
    }

    private KnowledgePointDirectoryException invalid(List<KnowledgePointDirectoryErrorVo.FieldErrorVo> fieldErrors) {
        return new KnowledgePointDirectoryException(400, "KNOWLEDGE_POINT_LOOKUP_INVALID", "请求参数不正确",
            false, fieldErrors, null);
    }
}
