package org.dromara.certmuse.shared.web;

import org.springframework.core.Ordered;

/**
 * Explicit ordering for CertMuse exception boundaries.
 */
public final class CertMuseAdviceOrder {
    /** Domain contract adapters must run before the module-wide boundary. */
    public static final int DOMAIN = 100;

    /** Module-wide handling must run before the RuoYi global fallback. */
    public static final int MODULE = Ordered.LOWEST_PRECEDENCE - 100;

    private CertMuseAdviceOrder() {
    }
}
