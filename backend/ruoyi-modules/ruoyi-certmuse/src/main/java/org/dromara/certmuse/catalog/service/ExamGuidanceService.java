package org.dromara.certmuse.catalog.service;

import java.util.Map;
import java.util.List;
import org.dromara.certmuse.catalog.domain.vo.PublicQualificationVo;
import tools.jackson.databind.JsonNode;

/** U16 exam-period aggregate use cases. */
public interface ExamGuidanceService {
    Map<String, Object> listPeriods(Integer examYear, String half, String status, String periodCode, Integer pageNum, Integer pageSize);
    Map<String, Object> periodDetail(String periodId);
    Map<String, Object> createPeriod(String requestId, JsonNode command);
    Map<String, Object> updatePeriod(String periodId, String requestId, JsonNode command);
    void deletePeriod(String periodId, String requestId, JsonNode command);
    Map<String, Object> createRevision(String periodId, String requestId, JsonNode command);
    Map<String, Object> publish(String periodId, String requestId, JsonNode command);
    Map<String, Object> saveSchedule(String periodId, String scheduleId, String requestId, JsonNode command);
    Map<String, Object> deleteSchedule(String scheduleId, String requestId, JsonNode command);
    Map<String, Object> listRegions(String keyword, String status, String regionType, Integer pageNum, Integer pageSize);
    Map<String, Object> regionDetail(String regionId);
    Map<String, Object> updateRegion(String regionId, String requestId, JsonNode command);
    Map<String, Object> saveRegistration(String periodId, String registrationId, String requestId, JsonNode command);
    Map<String, Object> deleteRegistration(String registrationId, String requestId, JsonNode command);
    Map<String, Object> qualificationContext(Integer targetYear, String targetHalf);
    List<PublicQualificationVo> publicQualifications();
    Map<String, Object> publicRegions(String periodCode);
}
