package org.dromara.certmuse.assessment.support;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.dromara.certmuse.assessment.domain.SubjectiveAnswerValidation;
import tools.jackson.databind.JsonNode;

/** Shared validation for frozen CASE and ESSAY answer payloads and AI scores. */
public final class SubjectiveAnswerValidator {
    public static final int MAX_CODE_POINTS = 20_000;
    private static final Set<String> TYPES = Set.of("CASE", "ESSAY");

    private SubjectiveAnswerValidator() {
    }

    public static SubjectiveAnswerValidation validate(JsonNode answer, String questionType) {
        if (!TYPES.contains(questionType) || answer == null) return SubjectiveAnswerValidation.invalid();
        String version = answer.path("schema_version").asText(answer.path("schemaVersion").asText());
        String type = answer.path("question_type").asText(answer.path("questionType").asText());
        String value = answer.path("value").asText(null);
        if (!AssessmentJsonSchema.SUBJECTIVE_ANSWER.version().equals(version)
            || !questionType.equals(type) || value == null || value.isBlank()
            || value.codePointCount(0, value.length()) > MAX_CODE_POINTS) {
            return SubjectiveAnswerValidation.invalid();
        }
        return new SubjectiveAnswerValidation(true, value);
    }

    public static BigDecimal scoreRate(JsonNode result) {
        try {
            BigDecimal rate = new BigDecimal(result.path("scoreRate").asText());
            if (rate.signum() < 0 || rate.compareTo(BigDecimal.ONE) > 0) return null;
            return rate.setScale(8, RoundingMode.HALF_UP);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /** Validates a grading result against its frozen rubric and returns the recomputed rate. */
    public static GradingResult gradingResult(JsonNode rubric, JsonNode result) {
        if (rubric == null || result == null || !rubric.path("items").isArray()
            || rubric.path("items").isEmpty() || !result.path("itemResults").isArray()
            || !containsChinese(result.path("feedback").asText())) {
            return null;
        }
        Map<String, BigDecimal> weights = new HashMap<>();
        BigDecimal weightTotal = BigDecimal.ZERO;
        try {
            for (JsonNode item : rubric.path("items")) {
                String code = item.path("code").asText();
                BigDecimal weight = new BigDecimal(item.path("weight").asText());
                if (code.isBlank() || item.path("description").asText().isBlank()
                    || weight.signum() <= 0 || weights.put(code, weight) != null) return null;
                weightTotal = weightTotal.add(weight);
            }
            if (weightTotal.subtract(BigDecimal.ONE).abs().compareTo(new BigDecimal("0.0001")) > 0) return null;
            Set<String> seen = new HashSet<>();
            BigDecimal recomputed = BigDecimal.ZERO;
            for (JsonNode item : result.path("itemResults")) {
                String code = item.path("code").asText();
                BigDecimal rate = new BigDecimal(item.path("scoreRate").asText());
                BigDecimal weight = weights.get(code);
                if (weight == null || !seen.add(code) || rate.signum() < 0 || rate.compareTo(BigDecimal.ONE) > 0) return null;
                recomputed = recomputed.add(weight.multiply(rate));
            }
            if (!seen.equals(weights.keySet())) return null;
            recomputed = recomputed.setScale(8, RoundingMode.HALF_UP);
            // The item-level rates are the authoritative, auditable grading evidence. Providers commonly round the
            // display-level scoreRate, so require it to be a valid rate but do not reject an otherwise valid result
            // solely because that convenience field differs from the recomputed weighted rate.
            if (scoreRate(result) == null) return null;
            return new GradingResult(recomputed, result.path("feedback").asText(), result.path("itemResults"));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /** Returns a safe machine-readable reason when a structured grading result cannot be accepted. */
    public static String gradingErrorCode(JsonNode rubric, JsonNode result) {
        if (rubric == null || !rubric.path("items").isArray() || rubric.path("items").isEmpty()) {
            return "AI_RUBRIC_INVALID";
        }
        if (result == null || !result.isObject()) return "AI_OUTPUT_NOT_OBJECT";
        if (!containsChinese(result.path("feedback").asText())) return "AI_OUTPUT_FEEDBACK_INVALID";
        if (!result.path("itemResults").isArray()) return "AI_OUTPUT_ITEMS_INVALID";
        if (scoreRate(result) == null) return "AI_OUTPUT_SCORE_RATE_INVALID";
        Set<String> expectedCodes = new HashSet<>();
        for (JsonNode item : rubric.path("items")) {
            String code = item.path("code").asText();
            if (code.isBlank() || !expectedCodes.add(code)) return "AI_RUBRIC_INVALID";
        }
        Set<String> actualCodes = new HashSet<>();
        for (JsonNode item : result.path("itemResults")) {
            String code = item.path("code").asText();
            if (!expectedCodes.contains(code) || !actualCodes.add(code)) return "AI_OUTPUT_ITEM_CODE_INVALID";
            try {
                BigDecimal rate = new BigDecimal(item.path("scoreRate").asText());
                if (rate.signum() < 0 || rate.compareTo(BigDecimal.ONE) > 0) return "AI_OUTPUT_ITEM_SCORE_INVALID";
            } catch (RuntimeException exception) {
                return "AI_OUTPUT_ITEM_SCORE_INVALID";
            }
        }
        return actualCodes.equals(expectedCodes) ? "AI_OUTPUT_INVALID" : "AI_OUTPUT_ITEMS_INCOMPLETE";
    }

    private static boolean containsChinese(String value) {
        if (value == null || value.isBlank()) return false;
        return value.codePoints().anyMatch(codePoint -> codePoint >= 0x4E00 && codePoint <= 0x9FFF);
    }

    public record GradingResult(BigDecimal scoreRate, String feedback, JsonNode itemResults) { }
}
