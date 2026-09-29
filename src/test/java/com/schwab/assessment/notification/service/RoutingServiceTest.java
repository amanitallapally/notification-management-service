package com.schwab.assessment.notification.service;

import com.schwab.assessment.notification.domain.ChannelType;
import com.schwab.assessment.notification.domain.Severity;
import com.schwab.assessment.notification.model.RecipientPreferenceEntity;
import com.schwab.assessment.notification.repository.RecipientPreferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoutingServiceTest {

    @Mock
    private RecipientPreferenceRepository recipientPreferenceRepository;

    private RoutingService routingService;

    @BeforeEach
    void setUp() {
        routingService = new RoutingService(recipientPreferenceRepository, "CRITICAL");
    }

    @Test
    void resolvesToRequestedChannelsWhenNoPreferenceExists() {
        when(recipientPreferenceRepository.findById("user-x")).thenReturn(Optional.empty());

        List<ChannelType> result = routingService.resolveChannels(
                "user-x", List.of(ChannelType.EMAIL, ChannelType.SMS), Severity.MEDIUM);

        assertThat(result).containsExactly(ChannelType.EMAIL, ChannelType.SMS);
    }

    @Test
    void filtersRequestedChannelsByRecipientPreferenceOrder() {
        RecipientPreferenceEntity pref = RecipientPreferenceEntity.builder()
                .recipientId("user-1")
                .preferredChannels("SMS,EMAIL")
                .build();
        when(recipientPreferenceRepository.findById("user-1")).thenReturn(Optional.of(pref));

        List<ChannelType> result = routingService.resolveChannels(
                "user-1", List.of(ChannelType.EMAIL, ChannelType.SMS, ChannelType.PUSH), Severity.MEDIUM);

        // Preference order wins, and PUSH (not preferred) is dropped
        assertThat(result).containsExactly(ChannelType.SMS, ChannelType.EMAIL);
    }

    @Test
    void fallsBackToRequestedChannelsWhenPreferenceDoesNotIntersect() {
        RecipientPreferenceEntity pref = RecipientPreferenceEntity.builder()
                .recipientId("user-1")
                .preferredChannels("PUSH")
                .build();
        when(recipientPreferenceRepository.findById("user-1")).thenReturn(Optional.of(pref));

        List<ChannelType> result = routingService.resolveChannels(
                "user-1", List.of(ChannelType.EMAIL), Severity.MEDIUM);

        assertThat(result).containsExactly(ChannelType.EMAIL);
    }

    @Test
    void criticalSeverityEscalatesToAllRequestedChannelsIgnoringPreference() {
        List<ChannelType> result = routingService.resolveChannels(
                "user-1", List.of(ChannelType.EMAIL, ChannelType.SMS, ChannelType.PUSH), Severity.CRITICAL);

        assertThat(result).containsExactly(ChannelType.EMAIL, ChannelType.SMS, ChannelType.PUSH);
    }
}
