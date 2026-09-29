package com.schwab.assessment.notification.service.provider;

import com.schwab.assessment.notification.domain.ChannelType;
import org.springframework.stereotype.Component;

/**
 * Brownfield-scenario addition (requirement 3.2): a new WEBHOOK channel added
 * without modifying the delivery orchestrator, thanks to the
 * {@link NotificationProvider} strategy interface and {@link ProviderRegistry}.
 */
@Component
public class WebhookProvider extends AbstractSimulatedProvider {
    @Override
    protected ChannelType channelType() {
        return ChannelType.WEBHOOK;
    }

    @Override
    protected String doSend(ProviderDeliveryRequest request) {
        return "WEBHOOK: POST delivered, ack=200 for " + request.recipientId();
    }
}
