package org.dromara.certmuse.question.service;

import org.dromara.certmuse.question.domain.bo.CollectionQueryBo;
import org.dromara.certmuse.question.domain.bo.CollectionRenameBo;
import org.dromara.certmuse.question.domain.bo.CollectionRevisionCreateBo;
import org.dromara.certmuse.question.domain.bo.CollectionSaveBo;
import org.dromara.certmuse.question.domain.vo.CollectionDetailVo;
import org.dromara.certmuse.question.domain.vo.CollectionListVo;
import org.dromara.certmuse.question.domain.vo.CollectionManageVo;
import org.dromara.certmuse.question.domain.vo.CollectionMutationVo;
import org.dromara.certmuse.question.domain.vo.CollectionRenameVo;
import org.dromara.certmuse.question.domain.vo.CollectionRevisionDetailVo;
import org.dromara.common.core.domain.PageResult;

public interface CollectionService {
    /** Returns whether this published revision still passes all frozen first-diagnostic publication checks. */
    boolean isFirstDiagnosticReady(long revisionId);
    /**
     * 分页查询管理端当前应展示的题集修订。
     */
    PageResult<CollectionListVo> list(CollectionQueryBo query);

    /** 按题集父行分页返回管理页所需的完整修订摘要。 */
    PageResult<CollectionManageVo> manageList(CollectionQueryBo query);

    /**
     * 查询题集、当前发布修订及完整历史。
     */
    CollectionDetailVo detail(String collectionId);

    /**
     * 查询指定修订的完整内容。
     */
    CollectionRevisionDetailVo revisionDetail(String revisionId);

    /**
     * 创建题集稳定身份和 V1 草稿。
     */
    CollectionMutationVo create(String requestId, CollectionSaveBo command);

    /**
     * 使用乐观锁保存草稿内容。
     */
    CollectionMutationVo save(String revisionId, String requestId, CollectionSaveBo command);

    /**
     * 修改题集父级稳定名称，不改变任何修订快照。
     */
    CollectionRenameVo rename(String collectionId, String requestId, CollectionRenameBo command);

    /**
     * 真实删除尚未提交审核的草稿修订。
     */
    CollectionMutationVo deleteDraft(String revisionId, String requestId);

    /**
     * 从指定历史修订复制新的草稿版本。
     */
    CollectionMutationVo createRevision(String collectionId, String requestId, CollectionRevisionCreateBo command);

    /**
     * 将草稿提交至审核。
     */
    CollectionMutationVo submitReview(String revisionId, String requestId);

    /**
     * 审核通过修订并切换当前发布指针。
     */
    CollectionMutationVo approve(String revisionId, String requestId);

    /**
     * 驳回审核中的修订至草稿并记录意见。
     */
    CollectionMutationVo reject(String revisionId, String requestId, String reviewOpinion);

    /** 下架当前已发布修订并将其退回草稿。 */
    CollectionMutationVo offline(String revisionId, String requestId);
}
