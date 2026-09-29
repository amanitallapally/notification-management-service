package com.schwab.assessment.notification.service;

import com.schwab.assessment.notification.domain.ChannelType;
import com.schwab.assessment.notification.domain.Severity;
import com.schwab.assessment.notification.repository.RecipientPreferenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Comparator;
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

    private static final Logger log = LoggerFactory.getLogger(RoutingService.class);

    private final RecipientPreferenceRepository recipientPreferenceRepository;
    private final Set<Severity> escalateSeverities;
    private final List<ChannelType> defaultChannelOrder;

    public RoutingService(RecipientPreferenceRepository recipientPreferenceRepository,
                           @Value("${notification.routing.escalate-severities:CRITICAL}") String escalateSeveritiesCsv,
                           @Value("${notification.routing.default-channel-order:}") String defaultChannelOrderCsv) {
        this.recipientPreferenceRepository = recipientPreferenceRepository;
        this.escalateSeverities = new LinkedHashSet<>();
        for (String s : escalateSeveritiesCsv.split(",")) {
            if (!s.isBlank()) {
                escalateSeverities.add(Severity.valueOf(s.trim()));
            }
        }
        this.defaultChannelOrder = defaultChannelOrderCsv.isBlank()
                ? List.of()
                : List.of(defaultChannelOrderCsv.split(",")).stream().map(String::trim).map(ChannelType::valueOf).toList();
    }

    public List<ChannelType> resolveChannels(String recipientId, List<ChannelType> requestedChannels, Severity severity) {
        if (escalateSeverities.contains(severity)) {
            log.debug("Escalating recipient={} severity={} to all requested channels={}", recipientId, severity, requestedChannels);
            return List.copyOf(requestedChannels);
        }

        List<ChannelType> resolved = recipientPreferenceRepository.findById(recipientId)
                .map(pref -> {
                    List<ChannelType> preferred = pref.preferredChannelList();
                    List<ChannelType> filtered = preferred.stream()
                            .filter(requestedChannels::contains)
                            .toList();
                    // If preference doesn't intersect the request at all, fall back to
                    // the configured default order instead of the raw request order.
                    return filtered.isEmpty() ? applyDefaultOrder(requestedChannels) : filtered;
                })
                .orElseGet(() -> applyDefaultOrder(requestedChannels));
        log.debug("Resolved channels={} for recipient={} requested={} severity={}", resolved, recipientId, requestedChannels, severity);
        return resolved;
    }

    /**
     * Orders requested channels by the configured default fallback order
     * (requirement 4.3 "Routing policy" factor); channels absent from that
     * configured order keep their original relative position, appended
     * after the ones that are listed. Sort is stable, so ties preserve
     * encounter order.
     */
    private List<ChannelType> applyDefaultOrder(List<ChannelType> requestedChannels) {
        if (defaultChannelOrder.isEmpty()) {
            return List.copyOf(requestedChannels);
        }
        return requestedChannels.stream()
                .sorted(Comparator.comparingInt(c -> {
                    int idx = defaultChannelOrder.indexOf(c);
                    return idx < 0 ? Integer.MAX_VALUE : idx;
                }))
                .toList();
    }
}

