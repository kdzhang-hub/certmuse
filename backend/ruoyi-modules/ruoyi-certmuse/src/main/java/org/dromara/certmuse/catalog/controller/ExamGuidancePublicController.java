package org.dromara.certmuse.catalog.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import lombok.RequiredArgsConstructor;
import org.dromara.certmuse.catalog.service.ExamGuidanceService;
import org.dromara.common.core.domain.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;
import java.util.List;
import org.dromara.certmuse.catalog.domain.vo.PublicQualificationVo;

/** Anonymous U16 read endpoints. */
@SaIgnore
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/public/exam-guidance")
public class ExamGuidancePublicController {
    private final ExamGuidanceService service;
    @GetMapping("/qualifications") public R<List<PublicQualificationVo>> qualifications(){return R.ok(service.publicQualifications());}
    @GetMapping("/qualification-context") public R<Map<String,Object>> context(@RequestParam(required=false) Integer targetYear,@RequestParam String targetHalf){return R.ok(service.qualificationContext(targetYear,targetHalf));}
    @GetMapping("/regions") public R<Map<String,Object>> regions(@RequestParam(required=false) String periodCode){return R.ok(service.publicRegions(periodCode));}
}
