package org.dromara.certmuse.shared.web;

/**
 * Machine-readable validation failure for one request field.
 */
public record ApiFieldError(String field, String code, String message) {
}
