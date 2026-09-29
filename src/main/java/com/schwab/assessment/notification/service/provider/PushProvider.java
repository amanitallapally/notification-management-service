package com.schwab.assessment.notification.service.provider;

import com.schwab.assessment.notification.domain.ChannelType;
import org.springframework.stereotype.Component;

@Component
public class PushProvider extends AbstractSimulatedProvider {
    @Override
    protected ChannelType channelType() {
        return ChannelType.PUSH;
    }
}
