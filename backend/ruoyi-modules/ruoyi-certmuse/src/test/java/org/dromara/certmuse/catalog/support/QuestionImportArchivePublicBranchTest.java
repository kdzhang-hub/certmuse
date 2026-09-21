package org.dromara.certmuse.catalog.support;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Public archive-entry coverage using real ZIP and stream boundaries. */
@Tag("dev")
class QuestionImportArchivePublicBranchTest {

    private static final byte[] QUESTION = "{\"qid\":\"q-1\"}\n".getBytes(StandardCharsets.UTF_8);

    @Test
    void exposesExtractedJsonlAndSupportedImagesUntilTheArchiveIsClosed() throws Exception {
        byte[] image = new byte[]{1, 2, 3, 4};
        Path jsonl;
        Path imagePath;

        try (QuestionImportArchive archive = extract(List.of(
            entry("images/", null),
            entry("questions.jsonl", QUESTION),
            entry("images/diagram.PNG", image),
            entry("images/photo.jpeg", new byte[]{5}),
            entry("images/animation.gif", new byte[]{6})
        ))) {
            jsonl = archive.jsonl();
            imagePath = archive.image("images/diagram.PNG");

            assertThat(Files.readAllBytes(jsonl)).isEqualTo(QUESTION);
            assertThat(Files.readAllBytes(imagePath)).isEqualTo(image);
            assertThat(archive.imageNames()).containsExactlyInAnyOrder(
                "images/diagram.PNG", "images/photo.jpeg", "images/animation.gif");
            assertThat(archive.image("images/missing.png")).isNull();
        }

        assertThat(jsonl).doesNotExist();
        assertThat(imagePath).doesNotExist();
    }

    @Test
    void rejectsAnEmptyQuestionsFileEvenWhenTheEntryExists() {
        assertValidation(
            zip(List.of(entry("questions.jsonl", new byte[0]))),
            "QUESTION_JSONL_REQUIRED", "file", "questions.jsonl 不存在或为空"
        );
    }

    @Test
    void rejectsUnexpectedDirectoriesAndRootFiles() {
        assertValidation(
            zip(List.of(entry("docs/", null), entry("questions.jsonl", QUESTION))),
            "QUESTION_ZIP_STRUCTURE_INVALID", "file", "ZIP 包含非法目录"
        );
        assertValidation(
            zip(List.of(entry("questions.jsonl", QUESTION), entry("readme.txt", QUESTION))),
            "QUESTION_ZIP_STRUCTURE_INVALID", "file", "ZIP 根目录结构不合法"
        );
        assertValidation(
            zip(List.of(entry("questions.jsonl", QUESTION), entry("images/nested/photo.png", new byte[]{1}))),
            "QUESTION_ZIP_STRUCTURE_INVALID", "file", "ZIP 根目录结构不合法"
        );
    }

    @Test
    void rejectsDuplicateQuestionAndImagePaths() {
        byte[] duplicateQuestion = zip(List.of(
            entry("questions.jsonl", QUESTION),
            entry("questions.jsonm", QUESTION)
        ));
        assertValidation(
            replaceAscii(duplicateQuestion, "questions.jsonm", "questions.jsonl"),
            "QUESTION_ZIP_DUPLICATE_PATH", "questions.jsonl", "ZIP 包含重复路径"
        );
    }

    @Test
    void rejectsAbsoluteBackslashAndTraversalEntryNames() {
        assertUnsafe("/questions.jsonl", "file");
        assertUnsafe("\\questions.jsonl", "file");
        assertUnsafe("images\\photo.png", "file");
        assertUnsafe("../questions.jsonl", "../questions.jsonl");
        assertUnsafe("images/../questions.jsonl", "images/../questions.jsonl");
        assertUnsafe("..hidden/questions.jsonl", "..hidden/questions.jsonl");
    }

    @Test
    void rejectsAnImageThatExceedsTheOneMiBExtractedLimit() {
        byte[] oversized = new byte[(int) QuestionImportArchive.MAX_IMAGE_BYTES + 1];
        fillIncompressible(oversized);

        assertValidation(
            zip(List.of(entry("questions.jsonl", QUESTION), entry("images/oversized.png", oversized))),
            "QUESTION_IMAGE_TOO_LARGE", "images/oversized.png", "ZIP 条目解压后超过限制"
        );
    }

    @Test
    void convertsCorruptZipMetadataIntoAStableValidationFailure() {
        byte[] archive = zip(List.of(entry("questions.jsonl", QUESTION)));
        byte[] corrupt = archive.clone();
        // Local header: signature (4), version (2), flags (2), compression method (2).
        corrupt[8] = 99;
        corrupt[9] = 0;

        assertValidation(
            corrupt,
            "QUESTION_ZIP_INVALID", "file", "ZIP 文件损坏、加密或格式不受支持"
        );
    }

    @Test
    void wrapsUnderlyingStreamFailuresAsArchiveReadFailures() {
        InputStream failing = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("storage unavailable");
            }

            @Override
            public int read(byte[] bytes, int offset, int length) throws IOException {
                throw new IOException("storage unavailable");
            }
        };

        assertThatThrownBy(() -> QuestionImportArchive.extract(failing))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("读取题目 ZIP 失败")
            .hasCauseInstanceOf(IOException.class);
    }

    private static void assertUnsafe(String name, String fieldPath) {
        assertValidation(
            zip(List.of(entry(name, QUESTION))),
            "QUESTION_ZIP_PATH_INVALID", fieldPath, "ZIP 路径不安全"
        );
    }

    private static void assertValidation(byte[] archive, String code, String fieldPath, String message) {
        assertThatThrownBy(() -> {
            try (QuestionImportArchive ignored = QuestionImportArchive.extract(new ByteArrayInputStream(archive))) {
                // The public extraction operation must reject this archive.
            }
        }).isInstanceOfSatisfying(QuestionImportValidationException.class, exception -> {
            assertThat(exception.code()).isEqualTo(code);
            assertThat(exception.fieldPath()).isEqualTo(fieldPath);
            assertThat(exception.getMessage()).isEqualTo(message);
        });
    }

    private static QuestionImportArchive extract(List<ArchiveEntry> entries) {
        return QuestionImportArchive.extract(new ByteArrayInputStream(zip(entries)));
    }

    private static ArchiveEntry entry(String name, byte[] content) {
        return new ArchiveEntry(name, content);
    }

    private static byte[] zip(List<ArchiveEntry> entries) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(output)) {
                for (ArchiveEntry item : entries) {
                    zip.putNextEntry(new ZipEntry(item.name()));
                    if (item.content() != null) {
                        zip.write(item.content());
                    }
                    zip.closeEntry();
                }
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("failed to build test archive", exception);
        }
    }

    private static void fillIncompressible(byte[] bytes) {
        long state = 0x4d595df4d0f33173L;
        for (int index = 0; index < bytes.length; index++) {
            state ^= state << 13;
            state ^= state >>> 7;
            state ^= state << 17;
            bytes[index] = (byte) state;
        }
    }

    private static byte[] replaceAscii(byte[] source, String before, String after) {
        byte[] needle = before.getBytes(StandardCharsets.US_ASCII);
        byte[] replacement = after.getBytes(StandardCharsets.US_ASCII);
        if (needle.length != replacement.length) {
            throw new IllegalArgumentException("ZIP entry aliases must have equal length");
        }
        byte[] result = source.clone();
        int matches = 0;
        for (int offset = 0; offset <= result.length - needle.length; offset++) {
            boolean match = true;
            for (int index = 0; index < needle.length; index++) {
                if (result[offset + index] != needle[index]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                System.arraycopy(replacement, 0, result, offset, replacement.length);
                matches++;
                offset += needle.length - 1;
            }
        }
        if (matches != 2) {
            throw new IllegalStateException("expected one local and one central ZIP entry name");
        }
        return result;
    }

    private record ArchiveEntry(String name, byte[] content) {
    }
}
