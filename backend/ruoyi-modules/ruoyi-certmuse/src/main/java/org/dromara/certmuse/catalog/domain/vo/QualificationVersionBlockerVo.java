package org.dromara.certmuse.catalog.domain.vo;

/** A resource type that blocks M01 deletion. */
public record QualificationVersionBlockerVo(String type, String label, long count) {
}
