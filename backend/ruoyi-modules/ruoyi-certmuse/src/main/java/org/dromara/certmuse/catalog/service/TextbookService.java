package org.dromara.certmuse.catalog.service;

import java.util.Collection;
import org.dromara.certmuse.catalog.domain.bo.TextbookChunkQueryBo;
import org.dromara.certmuse.catalog.domain.bo.TextbookChunkUpdateBo;
import org.dromara.certmuse.catalog.domain.bo.TextbookQueryBo;
import org.dromara.certmuse.catalog.domain.bo.TextbookUpdateBo;
import org.dromara.certmuse.catalog.domain.vo.TextbookChunkDetailVo;
import org.dromara.certmuse.catalog.domain.vo.TextbookChunkListItemVo;
import org.dromara.certmuse.catalog.domain.vo.TextbookDetailVo;
import org.dromara.certmuse.catalog.domain.vo.TextbookListVo;
import org.dromara.certmuse.catalog.domain.vo.TextbookOptionsVo;
import org.dromara.common.core.domain.PageResult;

/** 教材及教材内容块的管理入口。 */
public interface TextbookService {

    /** 分页查询当前用户可见的教材。 */
    PageResult<TextbookListVo> list(TextbookQueryBo query);

    /** 查询教材筛选项。 */
    TextbookOptionsVo options();

    /** 查询教材详情。 */
    TextbookDetailVo detail(String textbookId);

    /** 更新草稿或已发布教材的名称和绑定资格。 */
    void updateTextbook(String textbookId, TextbookUpdateBo command);

    /** 将草稿教材直接发布到学生端。 */
    void publishTextbook(String textbookId);

    /** 将已发布教材下架为草稿，学生端不再可见。 */
    void takeTextbookOffline(String textbookId);

    /** 分页查询教材内容块。 */
    PageResult<TextbookChunkListItemVo> chunks(String textbookId, TextbookChunkQueryBo query);

    /** 查询教材内容块详情。 */
    TextbookChunkDetailVo chunkDetail(String textbookId, String chunkId);

    /** 更新草稿教材的内容块。 */
    TextbookChunkDetailVo updateChunk(String textbookId, String chunkId, TextbookChunkUpdateBo command);

    /** 删除草稿教材中的内容块。 */
    void deleteChunks(String textbookId, Collection<String> rawIds);

    /** 删除草稿教材。 */
    void deleteTextbook(String textbookId);
}
