package org.dromara.certmuse.assessment;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.dromara.certmuse.assessment.controller.PastPaperController;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Static regression guards for the U15 public/answering boundary. */
@Tag("dev")
class PastPaperContractTest {
    @Test
    void publicPreviewAndLoginOnlyRevealStaySeparated() throws Exception {
        assertThat(PastPaperController.class.getMethod("preview", long.class).getAnnotations())
            .noneMatch(annotation -> annotation.annotationType().getSimpleName().equals("SaCheckLogin"));
        assertThat(PastPaperController.class.getMethod("reveal", long.class, int.class, String.class).getAnnotations())
            .anyMatch(annotation -> annotation.annotationType().getSimpleName().equals("SaCheckLogin"));
    }

    @Test
    void globalSecurityOnlyExcludesTheReadOnlyPublicBrowsePaths() throws Exception {
        String yaml = Files.readString(find("ruoyi-admin/src/main/resources/application.yml"));

        assertThat(yaml).contains(
            "/api/assessment/past-papers",
            "/api/assessment/past-papers/setup",
            "/api/assessment/past-papers/*/preview"
        );
        assertThat(yaml).doesNotContain("/api/assessment/past-papers/**");
    }

    @Test
    void migrationFreezesTimingDisclosureAndConcurrencyGuards() throws Exception {
        String sql = Files.readString(find("script/sql/postgres/certmuse/20260819_add-past-paper.sql"));
        assertThat(sql).contains("PAST_PAPER", "deadline_time", "formal_attempt_no", "cm_past_paper_answer_disclosure",
            "uk_cm_learning_session_active_past_paper_exam", "idx_cm_learning_session_past_paper_deadline");
        assertThat(Files.readString(find("script/sql/postgres/certmuse/local-migration-manifest.txt")))
            .contains("20260819_add-past-paper.sql");
    }

    @Test
    void implementationKeepsSignedImagesAndBackgroundResultDispatch() throws Exception {
        String service = Files.readString(find("ruoyi-modules/ruoyi-certmuse/src/main/java/org/dromara/certmuse/assessment/service/impl/PastPaperServiceImpl.java"));
        String mapper = Files.readString(find("ruoyi-modules/ruoyi-certmuse/src/main/resources/mapper/certmuse/PastPaperMapper.xml"));
        assertThat(service).contains("DISCLOSURE_BLOCK", "REPEAT_WINDOW", "autoFinishExpired", "dispatchResults",
            "imageUrlService.accessUrl", "type.replace('_','-')");
        assertThat(mapper).contains("for update skip locked", "PAST_PAPER_RESULT", "cm_past_paper_draft");
        assertThat(mapper).contains("for update of s")
            .doesNotContain("group by s.id for update");
        assertThat(mapper).doesNotContain("storage_path as url");
    }

    @Test
    void practiceUsesIndependentImmediateFeedbackContracts() throws Exception {
        assertThat(PastPaperController.class.getMethod("startPractice", long.class, String.class,
            org.dromara.certmuse.assessment.domain.bo.PastPaperStartBo.class).getGenericReturnType().getTypeName())
            .contains("StartPastPaperPracticeVo");
        assertThat(PastPaperController.class.getMethod("submit", long.class, int.class, String.class,
            org.dromara.certmuse.assessment.domain.bo.PastPaperAnswerBo.class).getGenericReturnType().getTypeName())
            .contains("SubmitPastPaperPracticeItemVo");
        String contract = Files.readString(find("../docs/requirements/U15-历年真题练习模式后端接口契约V1.md"));
        assertThat(contract).contains("selectionMode: 'single'", "RETURN_PAST_PAPERS")
            .doesNotContain("selectionMode: 'single' | 'multiple'", "RETURN_KNOWLEDGE_PRACTICE");
    }

    private static Path find(String relative) {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relative);
            if (Files.exists(candidate)) return candidate;
            current = current.getParent();
        }
        throw new IllegalStateException("file not found: " + relative);
    }
}
