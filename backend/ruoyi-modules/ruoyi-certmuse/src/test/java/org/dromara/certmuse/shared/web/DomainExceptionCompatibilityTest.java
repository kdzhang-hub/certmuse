package org.dromara.certmuse.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.dromara.certmuse.catalog.support.CatalogQueryException;
import org.dromara.certmuse.catalog.support.ImportException;
import org.dromara.certmuse.catalog.support.QualificationVersionException;
import org.dromara.certmuse.catalog.support.TextbookException;
import org.dromara.certmuse.learning.support.LearningGoalException;
import org.dromara.certmuse.question.support.CollectionException;
import org.dromara.certmuse.question.support.QuestionException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("dev")
class DomainExceptionCompatibilityTest {
    private final IllegalStateException cause = new IllegalStateException("internal detail");

    @Test
    void causeAwareConstructorsPreservePublicMetadata() {
        var importFailure = new ImportException(500, "IMPORT_FAILURE", "导入失败", cause);
        var qualificationFailure = new QualificationVersionException(
            500, "QUALIFICATION_VERSION_FAILURE", "资格操作失败", cause);
        var textbookFailure = new TextbookException(500, "TEXTBOOK_DATA_INVALID", "教材数据损坏", cause);
        var learningFailure = new LearningGoalException(500, "LEARNING_GOAL_FAILURE", "学习目标失败", cause);
        var questionFailure = new QuestionException(500, "QUESTION_OPERATION_FAILURE", "题目操作失败", cause);
        var collectionFailure = new CollectionException(500, "COLLECTION_OPERATION_FAILURE", "题集操作失败", cause);

        assertThat(importFailure).hasCause(cause);
        assertThat(importFailure.code()).isEqualTo(500);
        assertThat(importFailure.errorCode()).isEqualTo("IMPORT_FAILURE");
        assertThat(importFailure.fieldErrors()).isEmpty();
        assertThat(qualificationFailure).hasCause(cause);
        assertThat(qualificationFailure.status()).isEqualTo(500);
        assertThat(qualificationFailure.data().errorCode()).isEqualTo("QUALIFICATION_VERSION_FAILURE");
        assertThat(textbookFailure).hasCause(cause);
        assertThat(textbookFailure.getErrorCode()).isEqualTo("TEXTBOOK_DATA_INVALID");
        assertThat(learningFailure).hasCause(cause);
        assertThat(learningFailure.data().errorCode()).isEqualTo("LEARNING_GOAL_FAILURE");
        assertThat(questionFailure).hasCause(cause);
        assertThat(questionFailure.getData().errorCode()).isEqualTo("QUESTION_OPERATION_FAILURE");
        assertThat(collectionFailure).hasCause(cause);
        assertThat(collectionFailure.getData().errorCode()).isEqualTo("COLLECTION_OPERATION_FAILURE");
    }

    @Test
    void catalogQueryUsesCommonBaseWithoutChangingItsContractAccessors() {
        var exception = new CatalogQueryException(404, "CATALOG_NOT_FOUND", "目录不存在", "trace-1");

        assertThat(exception).isInstanceOf(CertMuseApiException.class);
        assertThat(exception.status()).isEqualTo(404);
        assertThat(exception.errorCode()).isEqualTo("CATALOG_NOT_FOUND");
        assertThat(exception.traceId()).isEqualTo("trace-1");
        assertThat(exception.getMessage()).isEqualTo("目录不存在");
    }
}
