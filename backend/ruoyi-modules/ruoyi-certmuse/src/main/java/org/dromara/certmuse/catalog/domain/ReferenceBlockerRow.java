package org.dromara.certmuse.catalog.domain;

/** Non-zero deletion blocker projection. */
public record ReferenceBlockerRow(String type, String label, long count) {
}
