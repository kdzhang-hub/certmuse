package org.dromara.certmuse.catalog.domain;

/** A usable existing revision matched by a paper record's semantic content. */
public record PaperQuestionReuse(long questionId, long revisionId, String answerSchema) {
}
