package org.dromara.certmuse.assessment.domain;

/** Internal result of subjective-answer format validation. */
public record SubjectiveAnswerValidation(boolean valid, String value) {
    public static SubjectiveAnswerValidation invalid() {
        return new SubjectiveAnswerValidation(false, null);
    }
}
