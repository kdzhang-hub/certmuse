package org.dromara.certmuse.catalog.domain.bo;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

/** Multipart form shared by supported import types. */
@Data
public class ImportCreateBo {
    private MultipartFile file;
    private String importType;
    private String certificationId;
    private String syllabusVersionId;
    private String subjectMappings;
    private String examSubjectId;
    private String knowledgeSyllabusVersionId;
    private String templateVersion;
    private String mode;
    private String documentId;
    private String title;
    private String edition;
}
