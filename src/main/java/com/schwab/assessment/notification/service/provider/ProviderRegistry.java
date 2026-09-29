package com.schwab.assessment.notification.service.provider;

import com.schwab.assessment.notification.domain.ChannelType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Central lookup so the delivery orchestrator can resolve a provider by
 * channel without any if/else or switch on channel type. Adding the
 * brownfield WEBHOOK channel only required a new bean here - no orchestrator
 * changes (see memory-bank/scenarios.md#brownfield).
 */
@Component
public class ProviderRegistry {

    private final Map<ChannelType, NotificationProvider> providersByChannel = new EnumMap<>(ChannelType.class);

    public ProviderRegistry(List<NotificationProvider> providers) {
        providers.forEach(p -> providersByChannel.put(p.channel(), p));
    }

    public NotificationProvider resolve(ChannelType channel) {
        NotificationProvider provider = providersByChannel.get(channel);
        if (provider == null) {
            throw new IllegalStateException("No provider registered for channel " + channel);
        }
        return provider;
    }
}
