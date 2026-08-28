package org.dromara.certmuse.catalog.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.dromara.certmuse.catalog.domain.bo.TextbookChunkQueryBo;
import org.dromara.certmuse.catalog.domain.bo.TextbookChunkUpdateBo;
import org.dromara.certmuse.catalog.domain.bo.TextbookQueryBo;
import org.dromara.certmuse.catalog.domain.bo.TextbookUpdateBo;
import org.dromara.certmuse.catalog.domain.vo.TextbookChunkDetailVo;
import org.dromara.certmuse.catalog.domain.vo.TextbookChunkListItemVo;
import org.dromara.certmuse.catalog.domain.vo.TextbookDetailVo;
import org.dromara.certmuse.catalog.domain.vo.TextbookListVo;
import org.dromara.certmuse.catalog.domain.vo.TextbookOptionsVo;
import org.dromara.certmuse.catalog.service.TextbookService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/catalog/textbooks")
public class TextbookController {

    private final TextbookService service;

    @SaCheckPermission("certmuse:catalog:resource:list")
    @GetMapping
    public R<PageResult<TextbookListVo>> list(@ModelAttribute TextbookQueryBo query) {
        return R.ok(service.list(query));
    }

    @SaCheckPermission("certmuse:catalog:resource:list")
    @GetMapping("/options")
    public R<TextbookOptionsVo> options() {
        return R.ok(service.options());
    }

    @SaCheckPermission("certmuse:catalog:resource:query")
    @GetMapping("/{textbookId}")
    public R<TextbookDetailVo> detail(@PathVariable String textbookId) {
        return R.ok(service.detail(textbookId));
    }

    @SaCheckPermission("certmuse:catalog:resource:edit")
    @Log(title = "教材", businessType = BusinessType.UPDATE)
    @PutMapping("/{textbookId}")
    public R<Void> update(@PathVariable String textbookId, @Valid @RequestBody TextbookUpdateBo command) {
        service.updateTextbook(textbookId, command);
        return R.ok();
    }

    @SaCheckPermission("certmuse:catalog:resource:edit")
    @Log(title = "教材", businessType = BusinessType.UPDATE)
    @PostMapping("/{textbookId}/publish")
    public R<Void> publish(@PathVariable String textbookId) {
        service.publishTextbook(textbookId);
        return R.ok();
    }

    @SaCheckPermission("certmuse:catalog:resource:edit")
    @Log(title = "教材", businessType = BusinessType.UPDATE)
    @PostMapping("/{textbookId}/offline")
    public R<Void> offline(@PathVariable String textbookId) {
        service.takeTextbookOffline(textbookId);
        return R.ok();
    }

    @SaCheckPermission("certmuse:catalog:resource:query")
    @GetMapping("/{textbookId}/chunks")
    public R<PageResult<TextbookChunkListItemVo>> chunks(
        @PathVariable String textbookId,
        @ModelAttribute TextbookChunkQueryBo query
    ) {
        return R.ok(service.chunks(textbookId, query));
    }

    @SaCheckPermission("certmuse:catalog:resource:query")
    @GetMapping("/{textbookId}/chunks/{chunkId}")
    public R<TextbookChunkDetailVo> chunk(@PathVariable String textbookId, @PathVariable String chunkId) {
        return R.ok(service.chunkDetail(textbookId, chunkId));
    }

    @SaCheckPermission("certmuse:catalog:resource:edit")
    @Log(
        title = "教材内容块",
        businessType = BusinessType.UPDATE,
        isSaveRequestData = false,
        isSaveResponseData = false
    )
    @PutMapping("/{textbookId}/chunks/{chunkId}")
    public R<TextbookChunkDetailVo> update(
        @PathVariable String textbookId,
        @PathVariable String chunkId,
        @Valid @RequestBody TextbookChunkUpdateBo command
    ) {
        return R.ok(service.updateChunk(textbookId, chunkId, command));
    }

    @SaCheckPermission("certmuse:catalog:resource:remove")
    @Log(title = "教材内容块", businessType = BusinessType.DELETE)
    @DeleteMapping("/{textbookId}/chunks")
    public R<Void> deleteChunks(@PathVariable String textbookId, @RequestParam List<String> ids) {
        service.deleteChunks(textbookId, ids);
        return R.ok();
    }

    @SaCheckPermission("certmuse:catalog:resource:remove")
    @Log(title = "教材", businessType = BusinessType.DELETE)
    @DeleteMapping("/{textbookId}")
    public R<Void> delete(@PathVariable String textbookId) {
        service.deleteTextbook(textbookId);
        return R.ok();
    }
}
