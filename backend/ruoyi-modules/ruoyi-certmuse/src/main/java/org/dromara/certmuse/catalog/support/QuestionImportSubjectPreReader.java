package org.dromara.certmuse.catalog.support;

import org.dromara.certmuse.catalog.domain.vo.DerivedQuestionSubjectVo;
import org.dromara.certmuse.catalog.domain.vo.ExamSubjectOptionVo;
import org.dromara.certmuse.catalog.domain.vo.ImportFieldErrorVo;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.nio.charset.CharacterCodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reads only the per-question subject assignment needed before a batch is created. */
public final class QuestionImportSubjectPreReader {
    private final JsonMapper objectMapper;

    public QuestionImportSubjectPreReader(JsonMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<DerivedQuestionSubjectVo> read(Path zip, List<ExamSubjectOptionVo> availableSubjects) {
        Map<Integer, ExamSubjectOptionVo> subjects = new LinkedHashMap<>();
        availableSubjects.forEach(subject -> subjects.put(subject.subjectNo(), subject));
        Map<Integer, DerivedQuestionSubjectVo> derived = new LinkedHashMap<>();
        try (InputStream input = Files.newInputStream(zip); QuestionImportArchive archive = QuestionImportArchive.extract(input)) {
            QuestionImportWorker.readJsonLines(archive.jsonl(), (lineNo, line) -> {
                if (line == null) throw invalid("file", "INVALID_FORMAT", "questions.jsonl 存在超过 1MiB 的行");
                JsonNode row;
                try {
                    row = objectMapper.readTree(QuestionImportWorker.strictUtf8(line));
                } catch (CharacterCodingException exception) {
                    throw invalid("file", "INVALID_FORMAT", "questions.jsonl 不是合法 UTF-8");
                } catch (Exception exception) {
                    throw invalid("file", "INVALID_FORMAT", "questions.jsonl 包含非法 JSON 行");
                }
                if (row == null || !row.isObject()) throw invalid("file", "INVALID_FORMAT", "questions.jsonl 包含非对象行");
                JsonNode knowledge = row.path("knowledge_points");
                if (!knowledge.isArray() || knowledge.isEmpty()) {
                    throw invalid("questions[" + lineNo + "].knowledge_points", "REQUIRED", "题目知识点不能为空");
                }
                Set<Integer> questionSubjectNos = new LinkedHashSet<>();
                for (JsonNode node : knowledge) {
                    JsonNode value = node.get("subject_no");
                    if (value == null || !value.canConvertToInt() || !value.isIntegralNumber()) {
                        throw invalid("questions[" + lineNo + "].knowledge_points[].subject_no", "INVALID_FORMAT", "科目编号必须是整数");
                    }
                    questionSubjectNos.add(value.intValue());
                }
                for (Integer subjectNo : questionSubjectNos) {
                    ExamSubjectOptionVo subject = subjects.get(subjectNo);
                    if (subject == null) {
                        throw invalid("questions[" + lineNo + "].knowledge_points[].subject_no", "UNSUPPORTED", "科目编号无法映射到所选考纲资格");
                    }
                    derived.putIfAbsent(subjectNo, new DerivedQuestionSubjectVo(subjectNo, subject.id(), subject.label()));
                }
                if (derived.size() > 3) throw invalid("questions[].knowledge_points[].subject_no", "UNSUPPORTED", "一个 ZIP 最多包含 3 个科目");
            });
        } catch (QuestionImportValidationException exception) {
            throw invalid("file", "INVALID_FORMAT", exception.getMessage());
        } catch (ImportException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalid("file", "INVALID_FORMAT", "题目 ZIP 无法读取");
        }
        if (derived.isEmpty()) throw invalid("questions", "REQUIRED", "questions.jsonl 不能为空");
        return List.copyOf(derived.values());
    }

    private static ImportException invalid(String field, String code, String message) {
        return new ImportException(400, "IMPORT_CONTEXT_INVALID", "导入上下文不合法", false, null,
            List.of(new ImportFieldErrorVo(field, code, message)));
    }
}
