package org.dromara.certmuse.question.service;

import org.dromara.certmuse.question.domain.QuestionRows;
import org.dromara.certmuse.question.domain.bo.QuestionQueryBo;
import org.dromara.certmuse.question.mapper.CollectionMapper;
import org.dromara.certmuse.question.mapper.QuestionMapper;
import org.dromara.certmuse.question.service.impl.QuestionReviewSubmissionSupport;
import org.dromara.certmuse.question.service.impl.QuestionServiceImpl;
import org.dromara.certmuse.question.validation.QuestionSubmitReviewValidator;
import org.dromara.common.satoken.utils.LoginHelper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@Tag("dev")
class QuestionQueryServiceTest {
    private final QuestionMapper mapper = mock(QuestionMapper.class);
    private final QuestionImageUrlService imageUrlService = mock(QuestionImageUrlService.class);
    private final CollectionMapper collectionMapper = mock(CollectionMapper.class);
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final QuestionService service = new QuestionServiceImpl(mapper, jsonMapper, imageUrlService,
        new QuestionReviewSubmissionSupport(mapper, new QuestionSubmitReviewValidator(jsonMapper), jsonMapper),
        collectionMapper);

    @Test
    void listsQuestionsAfterValidatingTheSelectedScope() {
        QuestionQueryBo query = new QuestionQueryBo();
        query.setKeyword("  架构 ");
        query.setCertificationId("10");
        query.setSyllabusVersionId("20");
        query.setExamSubjectId("30");
        query.setKnowledgePointId("40");
        query.setQuestionType("CHOICE");
        query.setDifficulty("easy");
        query.setStatus("draft");
        query.setPageNum(2);
        query.setPageSize(10);

        QuestionRows.Knowledge metadata = new QuestionRows.Knowledge();
        metadata.setSyllabusVersionId(20L);
        QuestionRows.ListItem row = new QuestionRows.ListItem();
        row.setQuestionId("100");
        row.setRevisionId("200");
        row.setRevisionNo(3);
        row.setQuestionCode("Q-100");
        row.setSyllabusVersionId("20");
        row.setSyllabusVersionName("2026");
        row.setStemSummary("架构题");
        row.setExamSubjectId("30");
        row.setExamSubjectName("综合知识");
        row.setQuestionType("CHOICE");
        row.setDifficulty("easy");
        row.setStatus("draft");
        row.setHasReviewOpinion(true);
        row.setUpdatedTime(OffsetDateTime.parse("2026-08-10T10:00:00+08:00"));
        when(mapper.countSubjectInSyllabus(20L, 30L)).thenReturn(1);
        when(mapper.selectKnowledgeMetadata(List.of(40L))).thenReturn(List.of(metadata));
        when(mapper.selectQuestions(query, 10L, 20L, 30L, 40L, 7L, 10, 10L)).thenReturn(List.of(row));
        when(mapper.countQuestions(query, 10L, 20L, 30L, 40L, 7L)).thenReturn(1L);

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::isSuperAdmin).thenReturn(false);
            login.when(LoginHelper::getUserId).thenReturn(7L);

            var result = service.list(query);

            assertThat(result.getTotal()).isEqualTo(1L);
            assertThat(result.getRows()).singleElement().satisfies(item -> {
                assertThat(item.questionId()).isEqualTo("100");
                assertThat(item.hasReviewOpinion()).isTrue();
                assertThat(item.revisionNo()).isEqualTo(3);
            });
        }
    }

    @Test
    void assemblesDetailAndPreviewIncludingOptionsImagesKnowledgeAndScores() {
        QuestionRows.Revision revision = revision();
        QuestionRows.Knowledge knowledge = new QuestionRows.Knowledge();
        knowledge.setKnowledgePointId(40L);
        knowledge.setKnowledgePointLabel("软件架构");
        knowledge.setRelationRole("primary");
        knowledge.setSortOrder(0);
        knowledge.setSyllabusVersionId(20L);
        knowledge.setExamSubjectId(30L);
        knowledge.setLeaf(true);
        QuestionRows.Option option = new QuestionRows.Option();
        option.setLabel("A");
        option.setContent("正确");
        option.setSortOrder(1);
        QuestionRows.Image image = new QuestionRows.Image();
        image.setId(90L);
        image.setSortOrder(1);
        image.setSourceUrl("https://cdn.example/image.png");
        image.setAlt("架构图");
        QuestionRows.IdLabel syllabus = new QuestionRows.IdLabel();
        syllabus.setId("20");
        syllabus.setLabel("2026考纲");
        QuestionRows.IdLabel subject = new QuestionRows.IdLabel();
        subject.setId("30");
        subject.setLabel("综合知识");

        when(mapper.selectRevision(100L, 200L, 7L)).thenReturn(revision);
        when(mapper.selectKnowledge(200L)).thenReturn(List.of(knowledge));
        when(mapper.selectSyllabusLabel(20L)).thenReturn(syllabus);
        when(mapper.selectExamSubjectOptions(11L)).thenReturn(List.of(subject));
        when(mapper.selectImages(200L)).thenReturn(List.of(image));
        when(mapper.selectOptions(200L)).thenReturn(List.of(option));
        when(imageUrlService.accessUrl("https://cdn.example/image.png", null)).thenReturn("/oss/image.png");

        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::isSuperAdmin).thenReturn(false);
            login.when(LoginHelper::getUserId).thenReturn(7L);

            var detail = service.detail("100", "200");
            var preview = service.preview("100", "200");

            assertThat(detail.syllabusVersionName()).isEqualTo("2026考纲");
            assertThat(detail.answer()).isInstanceOf(java.util.Map.class);
            assertThat(detail.images()).singleElement().satisfies(item -> assertThat(item.url()).isEqualTo("/oss/image.png"));
            assertThat(detail.options()).singleElement().satisfies(item -> assertThat(item.content()).isEqualTo("正确"));
            assertThat(preview.reviewOpinion()).isEqualTo("依据不足");
            assertThat(preview.stem()).isEqualTo("题干");
        }
    }

    private static QuestionRows.Revision revision() {
        QuestionRows.Revision revision = new QuestionRows.Revision();
        revision.setQuestionId(100L);
        revision.setRevisionId(200L);
        revision.setRevisionNo(2);
        revision.setRowVersion(4L);
        revision.setQuestionCode("Q-100");
        revision.setCertificationId(11L);
        revision.setCertificationName("架构师");
        revision.setExamSubjectId(30L);
        revision.setExamSubjectName("综合知识");
        revision.setQuestionType("CHOICE");
        revision.setDifficulty("easy");
        revision.setEstimatedSeconds(60);
        revision.setStem("题干");
        revision.setAnswer("{\"schema_version\":\"1.0\",\"answer_type\":\"option_keys\",\"value\":[\"A\"]}");
        revision.setAnalysis("解析");
        revision.setCommonMistakes("错误");
        revision.setStatus("rejected");
        revision.setReviewOpinion("依据不足");
        revision.setUpdatedTime(OffsetDateTime.parse("2026-08-10T10:00:00+08:00"));
        return revision;
    }
}
