package org.dromara.certmuse.catalog.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

/** A bounded, temporary extraction of a question import ZIP. */
final class QuestionImportArchive implements AutoCloseable {
    static final int MAX_LINE_BYTES = 1_048_576;
    static final long MAX_IMAGE_BYTES = 1_048_576L;
    private static final long MAX_EXTRACTED_BYTES = 128L * 1024 * 1024;
    private static final int MAX_ENTRIES = 10_000;
    private static final double MAX_COMPRESSION_RATIO = 100D;
    private static final int BUFFER_SIZE = 16 * 1024;

    private final Path root;
    private final Path jsonl;
    private final Map<String, Path> images;

    private QuestionImportArchive(Path root, Path jsonl, Map<String, Path> images) {
        this.root = root;
        this.jsonl = jsonl;
        this.images = images;
    }

    static QuestionImportArchive extract(InputStream source) {
        Path root = null;
        try {
            root = Files.createTempDirectory("question-import-");
            Map<String, Path> files = new LinkedHashMap<>();
            long totalBytes = 0;
            int entryCount = 0;
            try (ZipInputStream zip = new ZipInputStream(source)) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if (++entryCount > MAX_ENTRIES) {
                        throw invalid("QUESTION_ZIP_ENTRY_LIMIT", "file", "ZIP 条目数量超过限制");
                    }
                    String name = normalizeEntryName(entry.getName());
                    if (entry.isDirectory()) {
                        if (!"images/".equals(name)) {
                            throw invalid("QUESTION_ZIP_STRUCTURE_INVALID", "file", "ZIP 包含非法目录");
                        }
                        continue;
                    }
                    if (!"questions.jsonl".equals(name) && !isImagePath(name)) {
                        throw invalid("QUESTION_ZIP_STRUCTURE_INVALID", "file", "ZIP 根目录结构不合法");
                    }
                    if (files.containsKey(name)) {
                        throw invalid("QUESTION_ZIP_DUPLICATE_PATH", name, "ZIP 包含重复路径");
                    }
                    Path target = root.resolve(name.replace('/', java.io.File.separatorChar)).normalize();
                    if (!target.startsWith(root)) {
                        throw invalid("QUESTION_ZIP_PATH_INVALID", name, "ZIP 路径不安全");
                    }
                    Files.createDirectories(target.getParent());
                    long entryBytes = copyEntry(zip, target, isImagePath(name) ? MAX_IMAGE_BYTES : MAX_EXTRACTED_BYTES,
                        MAX_EXTRACTED_BYTES - totalBytes, name);
                    totalBytes += entryBytes;
                    long compressedSize = entry.getCompressedSize();
                    if (compressedSize > 0 && entryBytes / (double) compressedSize > MAX_COMPRESSION_RATIO) {
                        throw invalid("QUESTION_ZIP_COMPRESSION_RATIO", name, "ZIP 条目压缩比超过限制");
                    }
                    files.put(name, target);
                }
            }
            Path jsonl = files.remove("questions.jsonl");
            if (jsonl == null || Files.size(jsonl) == 0) {
                throw invalid("QUESTION_JSONL_REQUIRED", "file", "questions.jsonl 不存在或为空");
            }
            return new QuestionImportArchive(root, jsonl, files);
        } catch (QuestionImportValidationException exception) {
            deleteTree(root);
            throw exception;
        } catch (ZipException exception) {
            deleteTree(root);
            throw invalid("QUESTION_ZIP_INVALID", "file", "ZIP 文件损坏、加密或格式不受支持");
        } catch (IOException exception) {
            deleteTree(root);
            throw new IllegalStateException("读取题目 ZIP 失败", exception);
        }
    }

    Path jsonl() {
        return jsonl;
    }

    Path image(String name) {
        return images.get(name);
    }

    Set<String> imageNames() {
        return images.keySet();
    }

    @Override
    public void close() {
        deleteTree(root);
    }

    private static long copyEntry(
        InputStream input,
        Path target,
        long entryLimit,
        long remainingTotal,
        String name
    ) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        long copied = 0;
        try (OutputStream output = Files.newOutputStream(target)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                copied += read;
                if (copied > entryLimit) {
                    throw invalid(isImagePath(name) ? "QUESTION_IMAGE_TOO_LARGE" : "QUESTION_ZIP_ENTRY_TOO_LARGE",
                        name, "ZIP 条目解压后超过限制");
                }
                if (copied > remainingTotal) {
                    throw invalid("QUESTION_ZIP_EXTRACTED_LIMIT", "file", "ZIP 解压总大小超过限制");
                }
                output.write(buffer, 0, read);
            }
        }
        return copied;
    }

    private static String normalizeEntryName(String name) {
        if (name == null || name.isBlank() || name.startsWith("/") || name.startsWith("\\") || name.contains("\\")) {
            throw invalid("QUESTION_ZIP_PATH_INVALID", "file", "ZIP 路径不安全");
        }
        String normalized = Paths.get(name).normalize().toString().replace('\\', '/');
        String comparable = name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
        if (!normalized.equals(comparable) || name.contains("../") || name.startsWith("..") || normalized.startsWith("..")) {
            throw invalid("QUESTION_ZIP_PATH_INVALID", name, "ZIP 路径不安全");
        }
        return name;
    }

    private static boolean isImagePath(String name) {
        return name.matches("images/[^/]+\\.(?i:png|jpe?g|gif)");
    }

    private static QuestionImportValidationException invalid(String code, String path, String message) {
        return new QuestionImportValidationException(code, path, message);
    }

    private static void deleteTree(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Temporary files are best-effort cleanup.
                }
            });
        } catch (IOException ignored) {
            // Temporary files are best-effort cleanup.
        }
    }
}
