package org.dromara.certmuse.assessment.domain.vo;

import java.time.OffsetDateTime;
import java.util.List;

/** Public persisted AI chat message. */
public record PracticeAiMessageVo(
    String id,
    int sequence,
    String role,
    String content,
    String status,
    String answerDisclosureMode,
    String errorCode,
    OffsetDateTime createdAt,
    OffsetDateTime completedAt,
    List<CitationVo> citations
) {
    public PracticeAiMessageVo {
        citations = citations == null ? List.of() : List.copyOf(citations);
    }

    /** Public-safe textbook source used by an assistant message. */
    public record CitationVo(String textbookId, String textbookTitle, String edition,
                             List<String> headingPath, Integer pageStart, Integer pageEnd) {
        public CitationVo {
            headingPath = headingPath == null ? List.of() : List.copyOf(headingPath);
        }
    }
}
