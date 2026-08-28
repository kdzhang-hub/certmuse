package org.dromara.certmuse.assessment.support;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Deterministic input safety and pre-submit answer-disclosure guard. */
@Component
public class PracticeAiContentSafetyPolicy {
    private static final Pattern CONTROL = Pattern.compile("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]");

    public boolean acceptsInput(String message) {
        return message != null && !CONTROL.matcher(message).find();
    }

    public boolean acceptsOutput(String content, String mode, List<String> correctLabels, String analysis) {
        if (!"GUIDANCE_ONLY".equals(mode)) return true;
        String normalized = content.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        for (String label : correctLabels) {
            String upper = label.toUpperCase(Locale.ROOT);
            if (normalized.contains("正确答案是" + upper)
                || normalized.contains("答案是" + upper)
                || normalized.contains("正确选项是" + upper)
                || normalized.contains("选择" + upper + "是正确")) return false;
        }
        if (analysis != null && analysis.length() >= 20) {
            String fragment = analysis.replaceAll("\\s+", "");
            fragment = fragment.substring(0, Math.min(20, fragment.length()));
            return fragment.isBlank() || !normalized.contains(fragment.toUpperCase(Locale.ROOT));
        }
        return true;
    }
}
