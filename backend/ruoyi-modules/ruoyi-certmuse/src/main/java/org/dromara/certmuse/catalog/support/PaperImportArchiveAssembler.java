package org.dromara.certmuse.catalog.support;

import org.dromara.certmuse.catalog.domain.vo.ImportFieldErrorVo;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Builds one standard question ZIP from the ZIP files selected in one paper folder. */
public final class PaperImportArchiveAssembler {
    private static final int BUFFER_SIZE = 16 * 1024;

    private PaperImportArchiveAssembler() {
    }

    /**
     * Copies one source ZIP unchanged or merges several independent question ZIPs into one standard ZIP.
     *
     * @return the persisted source filename
     */
    public static String assemble(List<MultipartFile> sourceFiles, Path target) throws IOException {
        if (sourceFiles.size() == 1) {
            try (InputStream input = sourceFiles.getFirst().getInputStream(); OutputStream output = Files.newOutputStream(target)) {
                input.transferTo(output);
            }
            return sourceFiles.getFirst().getOriginalFilename();
        }

        Path root = Files.createTempDirectory("paper-import-folder-");
        Path questions = root.resolve("questions.jsonl");
        Path images = root.resolve("images");
        Set<String> imageNames = new LinkedHashSet<>();
        try (OutputStream questionOutput = Files.newOutputStream(questions)) {
            boolean previousEndedWithLineFeed = true;
            for (MultipartFile sourceFile : sourceFiles) {
                try (InputStream input = sourceFile.getInputStream(); QuestionImportArchive archive = QuestionImportArchive.extract(input)) {
                    if (!previousEndedWithLineFeed) {
                        questionOutput.write('\n');
                    }
                    previousEndedWithLineFeed = copyJsonl(archive.jsonl(), questionOutput);
                    for (String imageName : archive.imageNames()) {
                        if (!imageNames.add(imageName)) {
                            throw invalid("同一题集文件夹中的 ZIP 不能包含同名图片：" + imageName);
                        }
                        Path image = images.resolve(imageName.substring("images/".length()));
                        Files.createDirectories(image.getParent());
                        Files.copy(archive.image(imageName), image);
                    }
                } catch (QuestionImportValidationException exception) {
                    throw invalid("题目 ZIP 格式不合法：" + exception.getMessage());
                }
            }
        }
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(target))) {
            writeZipEntry(output, "questions.jsonl", questions);
            if (Files.exists(images)) {
                try (var paths = Files.walk(images)) {
                    for (Path image : paths.filter(Files::isRegularFile).sorted().toList()) {
                        String name = "images/" + images.relativize(image).toString().replace('\\', '/');
                        writeZipEntry(output, name, image);
                    }
                }
            }
        } catch (ImportException exception) {
            Files.deleteIfExists(target);
            throw exception;
        } catch (IOException exception) {
            Files.deleteIfExists(target);
            throw exception;
        } finally {
            deleteTree(root);
        }
        return "paper-folder.zip";
    }

    private static boolean copyJsonl(Path jsonl, OutputStream output) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int last = -1;
        try (InputStream input = Files.newInputStream(jsonl)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
                last = buffer[read - 1];
            }
        }
        return last == '\n';
    }

    private static void writeZipEntry(ZipOutputStream output, String name, Path source) throws IOException {
        output.putNextEntry(new ZipEntry(name));
        try (InputStream input = Files.newInputStream(source)) {
            input.transferTo(output);
        }
        output.closeEntry();
    }

    private static void deleteTree(Path root) {
        if (root == null) return;
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Temporary extraction cleanup is best effort.
                }
            });
        } catch (IOException ignored) {
            // Temporary extraction cleanup is best effort.
        }
    }

    private static ImportException invalid(String message) {
        return new ImportException(
            400,
            "IMPORT_CONTEXT_INVALID",
            "导入上下文不合法",
            false,
            null,
            List.of(new ImportFieldErrorVo("files", "INVALID_FORMAT", message))
        );
    }
}
