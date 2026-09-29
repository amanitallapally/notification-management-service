package com.schwab.assessment.notification.model;

import com.schwab.assessment.notification.domain.ChannelType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Static recipient channel preference used by the routing policy. In this
 * prototype preferences are seeded/managed via a simple repository; a
 * production system would source these from a recipient-profile service.
 */
@Entity
@Table(name = "recipient_preferences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecipientPreferenceEntity {

    @Id
    private String recipientId;

    /** Comma-separated ordered list of preferred channels, most preferred first. */
    @Column(nullable = false)
    private String preferredChannels;

    public java.util.List<ChannelType> preferredChannelList() {
        return java.util.Arrays.stream(preferredChannels.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(ChannelType::valueOf)
                .toList();
    }
}
