package org.dromara.certmuse.learning.domain;

/** A pending session that must be abandoned before a goal switch. */
public record LearningGoalSwitchSessionRow(long id, String sessionType, String title, String status) {
}
