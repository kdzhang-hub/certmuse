package org.dromara.certmuse.learning.support;

import java.util.List;
import org.dromara.certmuse.shared.web.CertMuseApiException;

/** Internal state failure while resolving the learner onboarding route. */
public class OnboardingException extends CertMuseApiException {

    public OnboardingException(String message) {
        super(500, "ONBOARDING_STATE_INVALID", message, false, null, List.of(), null, null);
    }
}
