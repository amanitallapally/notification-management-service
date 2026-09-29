package com.schwab.assessment.notification.service;

import com.schwab.assessment.notification.domain.ChannelType;
import com.schwab.assessment.notification.domain.Severity;
import com.schwab.assessment.notification.repository.RecipientPreferenceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Channel routing policy (requirement 4.3). Resolves the final set of
 * channels for a single recipient from three ordered inputs:
 *   1. Requested/eligible channels from the submission request.
 *   2. Recipient's stored preference order, if any (used to prioritize /
 *      filter down the requested set).
 *   3. Severity escalation: CRITICAL (configurable) notifications escalate to
 *      every requested channel regardless of preference, to avoid a missed
 *      page.
 *
 * Documented assumption (ambiguous requirement scenario): the spec does not
 * define how these three factors interact when they conflict. This
 * implementation treats "requested channels" as a hard ceiling (never route
 * outside it), recipient preference as an ordering/filter within that
 * ceiling, and severity escalation as an override of the preference filter
 * only - not of the requested-channel ceiling. This keeps callers in control
 * of the maximum blast radius per notification while still respecting
 * recipient preference in the common case.
 */
@Service
public class RoutingService {

    private final RecipientPreferenceRepository recipientPreferenceRepository;
    private final Set<Severity> escalateSeverities;

    public RoutingService(RecipientPreferenceRepository recipientPreferenceRepository,
                           @Value("${notification.routing.escalate-severities:CRITICAL}") String escalateSeveritiesCsv) {
        this.recipientPreferenceRepository = recipientPreferenceRepository;
        this.escalateSeverities = new LinkedHashSet<>();
        for (String s : escalateSeveritiesCsv.split(",")) {
            if (!s.isBlank()) {
                escalateSeverities.add(Severity.valueOf(s.trim()));
            }
        }
    }

    public List<ChannelType> resolveChannels(String recipientId, List<ChannelType> requestedChannels, Severity severity) {
        if (escalateSeverities.contains(severity)) {
            return List.copyOf(requestedChannels);
        }

        return recipientPreferenceRepository.findById(recipientId)
                .map(pref -> {
                    List<ChannelType> preferred = pref.preferredChannelList();
                    List<ChannelType> filtered = preferred.stream()
                            .filter(requestedChannels::contains)
                            .toList();
                    // If preference doesn't intersect the request at all, fall back to the request as-is
                    return filtered.isEmpty() ? List.copyOf(requestedChannels) : filtered;
                })
                .orElseGet(() -> List.copyOf(requestedChannels));
    }
}
