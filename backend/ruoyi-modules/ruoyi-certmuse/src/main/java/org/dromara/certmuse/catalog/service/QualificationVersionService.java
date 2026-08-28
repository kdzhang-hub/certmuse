package org.dromara.certmuse.catalog.service;

import org.dromara.certmuse.catalog.domain.bo.QualificationQueryBo;
import org.dromara.certmuse.catalog.domain.bo.QualificationWriteBo;
import org.dromara.certmuse.catalog.domain.bo.SyllabusVersionWriteBo;
import org.dromara.certmuse.catalog.domain.vo.QualificationVo;
import org.dromara.certmuse.catalog.domain.vo.SyllabusVersionVo;
import org.dromara.common.core.domain.PageResult;

/** M01 qualification and direct-syllabus-version use cases. */
public interface QualificationVersionService {
    PageResult<QualificationVo> list(QualificationQueryBo query);
    QualificationVo createQualification(String requestId, QualificationWriteBo command);
    QualificationVo updateQualification(String certificationId, String requestId, QualificationWriteBo command);
    void deleteQualification(String certificationId, String requestId);
    SyllabusVersionVo createVersion(String certificationId, String requestId, SyllabusVersionWriteBo command);
    SyllabusVersionVo updateVersion(String certificationId, String versionId, String requestId, SyllabusVersionWriteBo command);
    void deleteVersion(String certificationId, String versionId, String requestId);
}
