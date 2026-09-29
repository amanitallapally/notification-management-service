package com.schwab.assessment.notification.service.provider;

import com.schwab.assessment.notification.domain.ChannelType;
import com.schwab.assessment.notification.exception.ProviderException;

/**
 * Strategy interface isolating provider/channel-specific delivery logic
 * (requirement 3.2 brownfield: "refactor provider-specific logic").
 * Each implementation owns its own failure classification so the orchestrator
 * never branches on channel type.
 */
public interface NotificationProvider {

    ChannelType channel();

    /**
     * Attempts delivery. Implementations must throw {@link ProviderException}
     * with an explicit {@code FailureType} on failure and must never throw
     * unchecked/unclassified exceptions.
     *
     * @return a short, non-sensitive summary of the provider response.
     */
    String send(ProviderDeliveryRequest request) throws ProviderException;
}
