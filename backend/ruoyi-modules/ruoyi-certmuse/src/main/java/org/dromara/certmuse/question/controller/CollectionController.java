package org.dromara.certmuse.question.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.certmuse.question.domain.bo.CollectionQueryBo;
import org.dromara.certmuse.question.domain.bo.CollectionRejectBo;
import org.dromara.certmuse.question.domain.bo.CollectionRenameBo;
import org.dromara.certmuse.question.domain.bo.CollectionRevisionCreateBo;
import org.dromara.certmuse.question.domain.bo.CollectionSaveBo;
import org.dromara.certmuse.question.domain.vo.CollectionDetailVo;
import org.dromara.certmuse.question.domain.vo.CollectionListVo;
import org.dromara.certmuse.question.domain.vo.CollectionManageVo;
import org.dromara.certmuse.question.domain.vo.CollectionMutationVo;
import org.dromara.certmuse.question.domain.vo.CollectionRenameVo;
import org.dromara.certmuse.question.domain.vo.CollectionRevisionDetailVo;
import org.dromara.certmuse.question.service.CollectionService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端题集及其修订生命周期接口。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class CollectionController {
    private final CollectionService service;

    /**
     * 分页查询当前展示的题集修订。
     */
    @SaCheckPermission("certmuse:question:collection:list")
    @GetMapping("/collections")
    public R<PageResult<CollectionListVo>> list(@ModelAttribute CollectionQueryBo query) {
        return R.ok(service.list(query));
    }

    /** 管理页使用父行分页的聚合列表，审核中心继续使用扁平列表接口。 */
    @SaCheckPermission("certmuse:question:collection:list")
    @GetMapping("/collections/manage")
    public R<PageResult<CollectionManageVo>> manageList(@ModelAttribute CollectionQueryBo query) {
        return R.ok(service.manageList(query));
    }

    /**
     * 查询题集及其修订历史。
     */
    @SaCheckPermission("certmuse:question:collection:list")
    @GetMapping("/collections/{collectionId}")
    public R<CollectionDetailVo> detail(@PathVariable String collectionId) {
        return R.ok(service.detail(collectionId));
    }

    /**
     * 查询指定题集修订的完整内容。
     */
    @SaCheckPermission("certmuse:question:collection:list")
    @GetMapping("/collection-revisions/{revisionId}")
    public R<CollectionRevisionDetailVo> revisionDetail(@PathVariable String revisionId) {
        return R.ok(service.revisionDetail(revisionId));
    }

    /**
     * 新建题集并创建首个草稿修订。
     */
    @SaCheckPermission("certmuse:question:collection:add")
    @Log(title = "题集", businessType = BusinessType.INSERT, isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/collections")
    public R<CollectionMutationVo> create(@RequestHeader("X-Request-Id") String requestId,
                                          @Valid @RequestBody CollectionSaveBo command) {
        return R.ok("题集草稿已创建", service.create(requestId, command));
    }

    /**
     * 保存草稿修订。
     */
    @SaCheckPermission("certmuse:question:collection:edit")
    @Log(title = "题集草稿", businessType = BusinessType.UPDATE, isSaveRequestData = false, isSaveResponseData = false)
    @PutMapping("/collection-revisions/{revisionId}")
    public R<CollectionMutationVo> save(@PathVariable String revisionId,
                                        @RequestHeader("X-Request-Id") String requestId,
                                        @Valid @RequestBody CollectionSaveBo command) {
        return R.ok("题集草稿已保存", service.save(revisionId, requestId, command));
    }

    /**
     * 仅在题集存在草稿修订时修改父级稳定名称，不改写任何修订快照。
     */
    @SaCheckPermission("certmuse:question:collection:edit")
    @Log(title = "题集名称", businessType = BusinessType.UPDATE, isSaveRequestData = false, isSaveResponseData = false)
    @PutMapping("/collections/{collectionId}/name")
    public R<CollectionRenameVo> rename(@PathVariable String collectionId,
                                        @RequestHeader("X-Request-Id") String requestId,
                                        @Valid @RequestBody CollectionRenameBo command) {
        return R.ok("题集名称已更新", service.rename(collectionId, requestId, command));
    }

    /**
     * 真实删除草稿修订；已发布和审核中修订不得删除。
     */
    @SaCheckPermission("certmuse:question:collection:remove")
    @Log(title = "题集草稿", businessType = BusinessType.DELETE, isSaveRequestData = false, isSaveResponseData = false)
    @DeleteMapping("/collection-revisions/{revisionId}")
    public R<CollectionMutationVo> deleteDraft(@PathVariable String revisionId,
                                               @RequestHeader("X-Request-Id") String requestId) {
        return R.ok("题集草稿已删除", service.deleteDraft(revisionId, requestId));
    }

    /**
     * 从指定历史修订创建草稿。
     */
    @SaCheckPermission("certmuse:question:collection:add")
    @Log(title = "题集修订", businessType = BusinessType.INSERT, isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/collections/{collectionId}/revisions")
    public R<CollectionMutationVo> createRevision(@PathVariable String collectionId,
                                                  @RequestHeader("X-Request-Id") String requestId,
                                                  @RequestBody(required = false) CollectionRevisionCreateBo command) {
        return R.ok("题集新修订已创建", service.createRevision(collectionId, requestId,
            command == null ? new CollectionRevisionCreateBo() : command));
    }

    /**
     * 将通过检查的草稿提交审核。
     */
    @SaCheckPermission(value = {"certmuse:question:collection:submit-review", "certmuse:question:submit-review"},
        mode = SaMode.AND)
    @Log(title = "题集提交审核", businessType = BusinessType.UPDATE, isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/collection-revisions/{revisionId}/submit-review")
    public R<CollectionMutationVo> submitReview(@PathVariable String revisionId,
                                                @RequestHeader("X-Request-Id") String requestId) {
        return R.ok("题集已提交审核", service.submitReview(revisionId, requestId));
    }

    /**
     * 审核通过并切换当前发布修订。
     */
    @SaCheckPermission("certmuse:question:collection:review")
    @Log(title = "题集审核发布", businessType = BusinessType.UPDATE, isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/collection-revisions/{revisionId}/approve")
    public R<CollectionMutationVo> approve(@PathVariable String revisionId,
                                          @RequestHeader("X-Request-Id") String requestId) {
        return R.ok("题集已审核并发布", service.approve(revisionId, requestId));
    }

    /**
     * 驳回审核中的修订并返回草稿。
     */
    @SaCheckPermission("certmuse:question:collection:review")
    @Log(title = "题集审核驳回", businessType = BusinessType.UPDATE, isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/collection-revisions/{revisionId}/reject")
    public R<CollectionMutationVo> reject(@PathVariable String revisionId,
                                         @RequestHeader("X-Request-Id") String requestId,
                                         @Valid @RequestBody CollectionRejectBo command) {
        return R.ok("题集已驳回草稿", service.reject(revisionId, requestId, command.getReviewOpinion()));
    }

    /** 下架当前发布版本，修订退回草稿。 */
    @SaCheckPermission("certmuse:question:collection:offline")
    @Log(title = "题集下架", businessType = BusinessType.UPDATE, isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/collection-revisions/{revisionId}/offline")
    public R<CollectionMutationVo> offline(@PathVariable String revisionId,
                                            @RequestHeader("X-Request-Id") String requestId) {
        return R.ok("题集已下架并退回草稿", service.offline(revisionId, requestId));
    }
}
