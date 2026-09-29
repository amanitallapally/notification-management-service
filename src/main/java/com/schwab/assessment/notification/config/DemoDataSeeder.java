package com.schwab.assessment.notification.config;

import com.schwab.assessment.notification.domain.ChannelType;
import com.schwab.assessment.notification.model.RecipientPreferenceEntity;
import com.schwab.assessment.notification.repository.RecipientPreferenceRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Seeds a couple of demo recipient preferences so the routing policy has
 * something to resolve out of the box. Not intended for production use.
 */
@Configuration
@Profile("!test")
public class DemoDataSeeder {

    @Bean
    public CommandLineRunner seedRecipientPreferences(RecipientPreferenceRepository repository) {
        return args -> {
            if (repository.count() > 0) {
                return;
            }
            repository.save(RecipientPreferenceEntity.builder()
                    .recipientId("user-1")
                    .preferredChannels(ChannelType.EMAIL + "," + ChannelType.SMS)
                    .build());
            repository.save(RecipientPreferenceEntity.builder()
                    .recipientId("user-2")
                    .preferredChannels(ChannelType.PUSH.name())
                    .build());
        };
    }
}
