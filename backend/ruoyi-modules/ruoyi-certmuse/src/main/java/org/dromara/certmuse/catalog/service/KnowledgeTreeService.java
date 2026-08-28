package org.dromara.certmuse.catalog.service;

import org.dromara.certmuse.catalog.domain.bo.SyllabusPublishedDateUpdateBo;
import org.dromara.certmuse.catalog.domain.vo.KnowledgeTreeVo;
import org.dromara.certmuse.catalog.domain.vo.SyllabusListVo;
import org.dromara.common.core.domain.PageResult;

/** 知识树的查询与删除入口。 */
public interface KnowledgeTreeService {

    /** 分页查询可用大纲版本。 */
    PageResult<SyllabusListVo> syllabuses(String keyword, String certificationId, String versionName, String status,
                                         Integer pageNum, Integer pageSize);

    /** 查询指定大纲版本的知识树。 */
    KnowledgeTreeVo knowledgeTree(String syllabusVersionId);

    /** 更新指定考纲版本的发布日期。 */
    void updateSyllabusPublishedDate(String syllabusVersionId, String requestId, SyllabusPublishedDateUpdateBo command);

    /** 删除指定大纲版本及其专属题目、教材、知识点和后代数据。 */
    void deleteKnowledgeTree(String syllabusVersionId);
}
