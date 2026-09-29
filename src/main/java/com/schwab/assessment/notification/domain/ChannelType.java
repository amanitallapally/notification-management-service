package com.schwab.assessment.notification.domain;

/**
 * Supported delivery channels. WEBHOOK added as the brownfield-scenario channel.
 */
public enum ChannelType {
    EMAIL,
    SMS,
    PUSH,
    WEBHOOK
}
