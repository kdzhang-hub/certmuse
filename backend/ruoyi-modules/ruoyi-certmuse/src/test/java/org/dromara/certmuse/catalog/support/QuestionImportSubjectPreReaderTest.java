package org.dromara.certmuse.catalog.support;

import org.dromara.certmuse.catalog.domain.vo.ExamSubjectOptionVo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class QuestionImportSubjectPreReaderTest {
    private final QuestionImportSubjectPreReader reader = new QuestionImportSubjectPreReader(JsonMapper.builder().build());
    private final List<ExamSubjectOptionVo> subjects = List.of(
        new ExamSubjectOptionVo("11", 1, "综合知识"),
        new ExamSubjectOptionVo("12", 2, "案例分析"),
        new ExamSubjectOptionVo("13", 3, "论文")
    );

    @Test
    void derivesAllSubjectsBeforeCreatingTheBatch() throws Exception {
        Path zip = zip(question(1) + "\n" + question(2) + "\n" + question(3));
        try {
            assertThat(reader.read(zip, subjects)).extracting(item -> item.subjectNo()).containsExactly(1, 2, 3);
        } finally {
            Files.deleteIfExists(zip);
        }
    }

    @Test
    void includesCrossSubjectQuestionInBatchScopeForAsyncValidation() throws Exception {
        Path zip = zip("{\"knowledge_points\":[{\"subject_no\":1},{\"subject_no\":2}]}");
        try {
            assertThat(reader.read(zip, subjects)).extracting(item -> item.subjectNo()).containsExactly(1, 2);
        } finally {
            Files.deleteIfExists(zip);
        }
    }

    @Test
    void rejectsUnknownSubjectNumber() throws Exception {
        Path zip = zip(question(4));
        try {
            assertThatThrownBy(() -> reader.read(zip, subjects)).isInstanceOf(ImportException.class)
                .extracting(error -> ((ImportException) error).fieldErrors().getFirst().code()).isEqualTo("UNSUPPORTED");
        } finally {
            Files.deleteIfExists(zip);
        }
    }

    private static String question(int subjectNo) {
        return "{\"knowledge_points\":[{\"subject_no\":" + subjectNo + ",\"code\":\"K1\"}]}";
    }

    private static Path zip(String jsonl) throws Exception {
        Path file = Files.createTempFile("question-subjects-", ".zip");
        try (var output = Files.newOutputStream(file); var zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("questions.jsonl"));
            zip.write(jsonl.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return file;
    }
}
