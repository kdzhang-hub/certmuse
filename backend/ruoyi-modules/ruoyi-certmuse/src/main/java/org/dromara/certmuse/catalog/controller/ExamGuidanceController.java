package org.dromara.certmuse.catalog.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.certmuse.catalog.service.ExamGuidanceService;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import java.util.Map;

/** Administrative U16 endpoints. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/catalog")
public class ExamGuidanceController {
    private final ExamGuidanceService service;
    @SaCheckPermission("certmuse:catalog:exam-schedule:list") @GetMapping("/exam-periods")
    public R<Map<String, Object>> periods(@RequestParam(required=false) Integer examYear, @RequestParam(required=false) String half,
        @RequestParam(required=false) String status, @RequestParam(required=false) String periodCode, @RequestParam(required=false) Integer pageNum, @RequestParam(required=false) Integer pageSize) { return R.ok(service.listPeriods(examYear, half, status, periodCode, pageNum, pageSize)); }
    @SaCheckPermission("certmuse:catalog:exam-schedule:list") @GetMapping("/exam-periods/{periodId}") public R<Map<String,Object>> detail(@PathVariable String periodId) { return R.ok(service.periodDetail(periodId)); }
    @SaCheckPermission("certmuse:catalog:exam-schedule:create") @Log(title="官方考试期次", businessType=BusinessType.INSERT) @PostMapping("/exam-periods") public R<Map<String,Object>> create(@RequestHeader("X-Request-Id") String id,@RequestBody JsonNode body){return R.ok(service.createPeriod(id,body));}
    @SaCheckPermission("certmuse:catalog:exam-schedule:edit") @Log(title="官方考试期次", businessType=BusinessType.UPDATE) @PutMapping("/exam-periods/{periodId}") public R<Map<String,Object>> update(@PathVariable String periodId,@RequestHeader("X-Request-Id") String id,@RequestBody JsonNode body){return R.ok(service.updatePeriod(periodId,id,body));}
    @SaCheckPermission("certmuse:catalog:exam-schedule:edit") @Log(title="官方考试期次", businessType=BusinessType.DELETE) @DeleteMapping("/exam-periods/{periodId}") public R<Void> delete(@PathVariable String periodId,@RequestHeader("X-Request-Id") String id,@RequestBody JsonNode body){service.deletePeriod(periodId,id,body);return R.ok();}
    @SaCheckPermission("certmuse:catalog:exam-schedule:create") @PostMapping("/exam-periods/{periodId}/revisions") public R<Map<String,Object>> revision(@PathVariable String periodId,@RequestHeader("X-Request-Id") String id,@RequestBody JsonNode body){return R.ok(service.createRevision(periodId,id,body));}
    @SaCheckPermission("certmuse:catalog:exam-schedule:publish") @PostMapping("/exam-periods/{periodId}/publish") public R<Map<String,Object>> publish(@PathVariable String periodId,@RequestHeader("X-Request-Id") String id,@RequestBody JsonNode body){return R.ok(service.publish(periodId,id,body));}
    @SaCheckPermission("certmuse:catalog:exam-schedule:edit") @PostMapping("/exam-periods/{periodId}/schedules") public R<Map<String,Object>> addSchedule(@PathVariable String periodId,@RequestHeader("X-Request-Id") String id,@RequestBody JsonNode body){return R.ok(service.saveSchedule(periodId,null,id,body));}
    @SaCheckPermission("certmuse:catalog:exam-schedule:edit") @PutMapping("/exam-schedules/{scheduleId}") public R<Map<String,Object>> updateSchedule(@PathVariable String scheduleId,@RequestHeader("X-Request-Id") String id,@RequestBody JsonNode body){return R.ok(service.saveSchedule(null,scheduleId,id,body));}
    @SaCheckPermission("certmuse:catalog:exam-schedule:edit") @DeleteMapping("/exam-schedules/{scheduleId}") public R<Map<String,Object>> deleteSchedule(@PathVariable String scheduleId,@RequestHeader("X-Request-Id") String id,@RequestBody JsonNode body){return R.ok(service.deleteSchedule(scheduleId,id,body));}
    @SaCheckPermission("certmuse:catalog:exam-schedule:list") @GetMapping("/exam-regions") public R<Map<String,Object>> regions(@RequestParam(required=false) String keyword,@RequestParam(required=false) String status,@RequestParam(required=false) String regionType,@RequestParam(required=false) Integer pageNum,@RequestParam(required=false) Integer pageSize){return R.ok(service.listRegions(keyword,status,regionType,pageNum,pageSize));}
    @SaCheckPermission("certmuse:catalog:exam-schedule:list") @GetMapping("/exam-regions/{regionId}") public R<Map<String,Object>> region(@PathVariable String regionId){return R.ok(service.regionDetail(regionId));}
    @SaCheckPermission("certmuse:catalog:exam-schedule:edit") @PutMapping("/exam-regions/{regionId}") public R<Map<String,Object>> updateRegion(@PathVariable String regionId,@RequestHeader("X-Request-Id") String id,@RequestBody JsonNode body){return R.ok(service.updateRegion(regionId,id,body));}
    @SaCheckPermission("certmuse:catalog:exam-schedule:edit") @PostMapping("/exam-periods/{periodId}/registrations") public R<Map<String,Object>> addRegistration(@PathVariable String periodId,@RequestHeader("X-Request-Id") String id,@RequestBody JsonNode body){return R.ok(service.saveRegistration(periodId,null,id,body));}
    @SaCheckPermission("certmuse:catalog:exam-schedule:edit") @PutMapping("/exam-region-registrations/{registrationId}") public R<Map<String,Object>> updateRegistration(@PathVariable String registrationId,@RequestHeader("X-Request-Id") String id,@RequestBody JsonNode body){return R.ok(service.saveRegistration(null,registrationId,id,body));}
    @SaCheckPermission("certmuse:catalog:exam-schedule:edit") @DeleteMapping("/exam-region-registrations/{registrationId}") public R<Map<String,Object>> deleteRegistration(@PathVariable String registrationId,@RequestHeader("X-Request-Id") String id,@RequestBody JsonNode body){return R.ok(service.deleteRegistration(registrationId,id,body));}
}
