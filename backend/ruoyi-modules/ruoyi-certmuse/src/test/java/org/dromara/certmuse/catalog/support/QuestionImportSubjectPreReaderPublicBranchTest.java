package org.dromara.certmuse.catalog.support;

import org.dromara.certmuse.catalog.domain.vo.ExamSubjectOptionVo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Public ZIP-input validation coverage for subject pre-reading before batch creation. */
@Tag("dev")
class QuestionImportSubjectPreReaderPublicBranchTest {

    private static final List<ExamSubjectOptionVo> THREE_SUBJECTS = List.of(
        new ExamSubjectOptionVo("11", 1, "综合知识"),
        new ExamSubjectOptionVo("12", 2, "案例分析"),
        new ExamSubjectOptionVo("13", 3, "论文")
    );

    private final QuestionImportSubjectPreReader reader = new QuestionImportSubjectPreReader(JsonMapper.builder().build());

    @Test
    void rejectsEmptyKnowledgePointsAndMissingKnowledgePointsWithFieldLevelErrors() throws Exception {
        assertFailure(
            zip("{\"knowledge_points\":[]}".getBytes(StandardCharsets.UTF_8)),
            THREE_SUBJECTS,
            "questions[1].knowledge_points",
            "REQUIRED",
            "题目知识点不能为空"
        );
        assertFailure(
            zip("{}".getBytes(StandardCharsets.UTF_8)),
            THREE_SUBJECTS,
            "questions[1].knowledge_points",
            "REQUIRED",
            "题目知识点不能为空"
        );
    }

    @Test
    void rejectsNonObjectUtf8AndJsonlSyntaxFailuresFromRealZipEntries() throws Exception {
        assertFailure(
            zip("\n".getBytes(StandardCharsets.UTF_8)),
            THREE_SUBJECTS,
            "file",
            "INVALID_FORMAT",
            "questions.jsonl 包含非对象行"
        );
        assertFailure(
            zip("[]".getBytes(StandardCharsets.UTF_8)),
            THREE_SUBJECTS,
            "file",
            "INVALID_FORMAT",
            "questions.jsonl 包含非对象行"
        );
        assertFailure(
            zip(new byte[]{(byte) 0xc3, 0x28}),
            THREE_SUBJECTS,
            "file",
            "INVALID_FORMAT",
            "questions.jsonl 不是合法 UTF-8"
        );
        assertFailure(
            zip("{not-json}".getBytes(StandardCharsets.UTF_8)),
            THREE_SUBJECTS,
            "file",
            "INVALID_FORMAT",
            "questions.jsonl 包含非法 JSON 行"
        );
    }

    @Test
    void rejectsMissingOutOfRangeAndNonIntegralSubjectNumbers() throws Exception {
        assertFailure(
            zip("{\"knowledge_points\":[{}]}".getBytes(StandardCharsets.UTF_8)),
            THREE_SUBJECTS,
            "questions[1].knowledge_points[].subject_no",
            "INVALID_FORMAT",
            "科目编号必须是整数"
        );
        assertFailure(
            zip("{\"knowledge_points\":[{\"subject_no\":2147483648}]}".getBytes(StandardCharsets.UTF_8)),
            THREE_SUBJECTS,
            "questions[1].knowledge_points[].subject_no",
            "INVALID_FORMAT",
            "科目编号必须是整数"
        );
        assertFailure(
            zip("{\"knowledge_points\":[{\"subject_no\":1.5}]}".getBytes(StandardCharsets.UTF_8)),
            THREE_SUBJECTS,
            "questions[1].knowledge_points[].subject_no",
            "INVALID_FORMAT",
            "科目编号必须是整数"
        );
    }

    @Test
    void rejectsArchivesThatDeriveMoreThanThreeDistinctSubjects() throws Exception {
        List<ExamSubjectOptionVo> fourSubjects = List.of(
            new ExamSubjectOptionVo("11", 1, "综合知识"),
            new ExamSubjectOptionVo("12", 2, "案例分析"),
            new ExamSubjectOptionVo("13", 3, "论文"),
            new ExamSubjectOptionVo("14", 4, "附加科目")
        );

        assertFailure(
            zip("{\"knowledge_points\":[{\"subject_no\":1},{\"subject_no\":2},{\"subject_no\":3},{\"subject_no\":4}]}"
                .getBytes(StandardCharsets.UTF_8)),
            fourSubjects,
            "questions[].knowledge_points[].subject_no",
            "UNSUPPORTED",
            "一个 ZIP 最多包含 3 个科目"
        );
    }

    @Test
    void convertsJsonlBomAndUnreadablePathsIntoStablePublicImportErrors() throws Exception {
        assertFailure(
            zip(new byte[0]),
            THREE_SUBJECTS,
            "file",
            "INVALID_FORMAT",
            "questions.jsonl 不存在或为空"
        );
        assertFailure(
            zip(new byte[]{(byte) 0xef, (byte) 0xbb, (byte) 0xbf, '{', '}'}),
            THREE_SUBJECTS,
            "file",
            "INVALID_FORMAT",
            "questions.jsonl 不能包含 BOM"
        );

        Path missing = Files.createTempFile("question-subject-reader-missing-", ".zip");
        Files.deleteIfExists(missing);
        assertFailure(
            missing,
            THREE_SUBJECTS,
            "file",
            "INVALID_FORMAT",
            "题目 ZIP 无法读取"
        );
    }

    @Test
    void rejectsAnOverOneMiBJsonlLineBeforeAttemptingToDecodeIt() throws Exception {
        byte[] line = new byte[QuestionImportArchive.MAX_LINE_BYTES + 2];
        String alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        Random random = new Random(42L);
        for (int index = 0; index < line.length - 1; index++) {
            line[index] = (byte) alphabet.charAt(random.nextInt(alphabet.length()));
        }
        line[line.length - 1] = '\n';

        assertFailure(
            zip(line),
            THREE_SUBJECTS,
            "file",
            "INVALID_FORMAT",
            "questions.jsonl 存在超过 1MiB 的行"
        );
    }

    private void assertFailure(
        Path zip,
        List<ExamSubjectOptionVo> subjects,
        String field,
        String code,
        String message
    ) throws Exception {
        try {
            assertThatThrownBy(() -> reader.read(zip, subjects))
                .isInstanceOfSatisfying(ImportException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(400);
                    assertThat(exception.errorCode()).isEqualTo("IMPORT_CONTEXT_INVALID");
                    assertThat(exception.fieldErrors()).singleElement().satisfies(error -> {
                        assertThat(error.field()).isEqualTo(field);
                        assertThat(error.code()).isEqualTo(code);
                        assertThat(error.message()).isEqualTo(message);
                    });
                });
        } finally {
            Files.deleteIfExists(zip);
        }
    }

    private static Path zip(byte[] jsonl) throws Exception {
        Path file = Files.createTempFile("question-subject-reader-", ".zip");
        try (var output = Files.newOutputStream(file); var zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("questions.jsonl"));
            zip.write(jsonl);
            zip.closeEntry();
        }
        return file;
    }
}
