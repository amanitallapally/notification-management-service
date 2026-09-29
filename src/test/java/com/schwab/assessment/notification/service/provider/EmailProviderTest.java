package com.schwab.assessment.notification.service.provider;

import com.schwab.assessment.notification.domain.ChannelType;
import com.schwab.assessment.notification.domain.FailureType;
import com.schwab.assessment.notification.domain.NotificationType;
import com.schwab.assessment.notification.domain.Severity;
import com.schwab.assessment.notification.exception.ProviderException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailProviderTest {

    private final EmailProvider provider = new EmailProvider();

    @Test
    void succeedsForNormalRecipient() {
        String result = provider.send(request("user-1", 1));
        assertThat(result).contains("user-1");
        assertThat(provider.channel()).isEqualTo(ChannelType.EMAIL);
    }

    @Test
    void classifiesInvalidRecipient() {
        assertThatThrownBy(() -> provider.send(request("invalid-user", 1)))
                .isInstanceOfSatisfying(ProviderException.class,
                        ex -> assertThat(ex.getFailureType()).isEqualTo(FailureType.INVALID_RECIPIENT));
    }

    @Test
    void classifiesRateLimited() {
        assertThatThrownBy(() -> provider.send(request("ratelimit-user", 1)))
                .isInstanceOfSatisfying(ProviderException.class,
                        ex -> assertThat(ex.getFailureType()).isEqualTo(FailureType.RATE_LIMITED));
    }

    @Test
    void classifiesTimeout() {
        assertThatThrownBy(() -> provider.send(request("timeout-user", 1)))
                .isInstanceOfSatisfying(ProviderException.class,
                        ex -> assertThat(ex.getFailureType()).isEqualTo(FailureType.TIMEOUT));
    }

    @Test
    void classifiesAuthError() {
        assertThatThrownBy(() -> provider.send(request("authfail-user", 1)))
                .isInstanceOfSatisfying(ProviderException.class,
                        ex -> assertThat(ex.getFailureType()).isEqualTo(FailureType.AUTH_ERROR));
    }

    @Test
    void classifiesPermanentRejection() {
        assertThatThrownBy(() -> provider.send(request("reject-user", 1)))
                .isInstanceOfSatisfying(ProviderException.class,
                        ex -> assertThat(ex.getFailureType()).isEqualTo(FailureType.PERMANENT_PROVIDER_REJECTION));
    }

    @Test
    void flakyRecipientFailsTransientlyThenSucceeds() {
        assertThatThrownBy(() -> provider.send(request("flaky-user", 1)))
                .isInstanceOfSatisfying(ProviderException.class,
                        ex -> assertThat(ex.getFailureType()).isEqualTo(FailureType.TRANSIENT_PROVIDER_FAILURE));

        String result = provider.send(request("flaky-user", 3));
        assertThat(result).contains("flaky-user");
    }

    private ProviderDeliveryRequest request(String recipientId, int attemptNumber) {
        return new ProviderDeliveryRequest("ntf_1", recipientId, "subject", Severity.MEDIUM, NotificationType.ALERT, attemptNumber);
    }
}
