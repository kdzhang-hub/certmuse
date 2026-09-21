package org.dromara.certmuse.shared.schema;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class JsonSchemaLiteralGuardTest {

    private static final Pattern DIRECT_SCHEMA_VALUE = Pattern.compile(
        "(?:\\\"schema_version\\\"|\\\"schemaVersion\\\")\\s*,\\s*\\\"(?:[a-z][a-z0-9_-]*/)?[0-9]+\\.[0-9]+\\\""
    );
    private static final Pattern REVERSED_SCHEMA_COMPARISON = Pattern.compile(
        "\\\"(?:[a-z][a-z0-9_-]*/)?[0-9]+\\.[0-9]+\\\"\\.equals\\([^)]*(?:schema_version|schemaVersion)"
    );
    private static final Pattern SCHEMA_CONSTANT = Pattern.compile(
        "(?i).*schema.*=\\s*\\\"(?:[a-z][a-z0-9_-]*/)?[0-9]+\\.[0-9]+\\\""
    );

    @Test
    void productionSchemaVersionsAreDeclaredOnlyBySchemaEnums() throws Exception {
        Path sourceRoot = find("ruoyi-modules/ruoyi-certmuse/src/main/java");
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            files.filter(path -> path.toString().endsWith(".java"))
                .filter(path -> !path.getFileName().toString().endsWith("JsonSchema.java"))
                .filter(path -> !path.getFileName().toString().equals("VersionedJsonDocumentFactory.java"))
                .filter(path -> !path.getFileName().toString().contains("LegacyIdempotencyPayloads"))
                .filter(path -> !path.getFileName().toString().equals("ExamGuidanceServiceImpl.java"))
                .forEach(path -> inspect(sourceRoot, path, violations));
        }

        assertThat(violations).as("schema version literals outside schema enums").isEmpty();
    }

    private static void inspect(Path sourceRoot, Path file, List<String> violations) {
        try {
            List<String> lines = Files.readAllLines(file);
            for (int index = 0; index < lines.size(); index++) {
                String line = lines.get(index);
                if (DIRECT_SCHEMA_VALUE.matcher(line).find()
                    || REVERSED_SCHEMA_COMPARISON.matcher(line).find()
                    || SCHEMA_CONSTANT.matcher(line).matches()) {
                    violations.add(sourceRoot.relativize(file) + ":" + (index + 1) + ": " + line.trim());
                }
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to inspect " + file, exception);
        }
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
