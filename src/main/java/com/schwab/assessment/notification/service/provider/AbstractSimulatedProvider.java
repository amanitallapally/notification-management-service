package com.schwab.assessment.notification.service.provider;

import com.schwab.assessment.notification.domain.ChannelType;
import com.schwab.assessment.notification.domain.FailureType;
import com.schwab.assessment.notification.exception.ProviderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared simulated-delivery logic extracted during the brownfield refactor
 * (see memory-bank/scenarios.md#brownfield). Originally each channel
 * duplicated its own random-failure simulation; this base class centralizes
 * it so concrete providers only declare their channel and any quirks.
 *
 * Failure simulation is deterministic and driven by recipient-id conventions
 * so tests can exercise every failure classification without real randomness:
 *   - recipient contains "invalid"   -> INVALID_RECIPIENT
 *   - recipient contains "ratelimit" -> RATE_LIMITED
 *   - recipient contains "timeout"   -> TIMEOUT
 *   - recipient contains "authfail"  -> AUTH_ERROR
 *   - recipient contains "reject"    -> PERMANENT_PROVIDER_REJECTION
 *   - recipient contains "flaky"     -> TRANSIENT_PROVIDER_FAILURE on first 2 attempts, then succeeds
 *   - anything else                  -> succeeds
 */
public abstract class AbstractSimulatedProvider implements NotificationProvider {

    private static final Logger log = LoggerFactory.getLogger(AbstractSimulatedProvider.class);

    @Override
    public final String send(ProviderDeliveryRequest request) throws ProviderException {
        String recipient = request.recipientId().toLowerCase();
        log.debug("[{}] attempting delivery to recipient={} notification={}",
                channel(), request.recipientId(), request.notificationId());

        if (recipient.contains("invalid")) {
            throw new ProviderException(FailureType.INVALID_RECIPIENT, channel() + ": recipient address is invalid");
        }
        if (recipient.contains("ratelimit")) {
            throw new ProviderException(FailureType.RATE_LIMITED, channel() + ": provider rate limit exceeded");
        }
        if (recipient.contains("timeout")) {
            throw new ProviderException(FailureType.TIMEOUT, channel() + ": provider request timed out");
        }
        if (recipient.contains("authfail")) {
            throw new ProviderException(FailureType.AUTH_ERROR, channel() + ": provider authentication failed");
        }
        if (recipient.contains("reject")) {
            throw new ProviderException(FailureType.PERMANENT_PROVIDER_REJECTION, channel() + ": provider permanently rejected message");
        }
        if (recipient.contains("flaky") && request.attemptNumber() < 3) {
            throw new ProviderException(FailureType.TRANSIENT_PROVIDER_FAILURE, channel() + ": transient provider failure (attempt " + request.attemptNumber() + ")");
        }

        return doSend(request);
    }

    /**
     * Hook for channel-specific behavior beyond the shared simulation rules.
     * Default implementation just returns a success summary.
     */
    protected String doSend(ProviderDeliveryRequest request) {
        return channel() + ": accepted by provider for " + request.recipientId();
    }

    protected abstract ChannelType channelType();

    @Override
    public final ChannelType channel() {
        return channelType();
    }
}
