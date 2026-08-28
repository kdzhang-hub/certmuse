package org.dromara.certmuse.catalog.domain.vo;

/**
 * Field-level import validation error.
 */
public record ImportFieldErrorVo(String field, String code, String message) {
}
