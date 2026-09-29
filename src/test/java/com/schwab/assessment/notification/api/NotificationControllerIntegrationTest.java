package com.schwab.assessment.notification.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schwab.assessment.notification.api.dto.NotificationRequest;
import com.schwab.assessment.notification.domain.ChannelType;
import com.schwab.assessment.notification.domain.NotificationType;
import com.schwab.assessment.notification.domain.Priority;
import com.schwab.assessment.notification.domain.Severity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end tests exercising submission, async delivery, retry, dedup, and
 * status/audit retrieval through the real HTTP layer against an in-memory H2
 * database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "notification.retry.max-attempts=3",
        "notification.retry.initial-backoff-ms=50",
        "notification.retry.max-backoff-ms=200",
        "notification.retry.poll-interval-ms=100"
})
class NotificationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void submitAndDeliverSucceeds() throws Exception {
        NotificationRequest request = new NotificationRequest(
                "trading-alerts", UUID.randomUUID().toString(), NotificationType.ALERT,
                Severity.HIGH, Priority.HIGH, List.of("user-1"), List.of(ChannelType.EMAIL),
                null, "Trade breached threshold", null, null);

        String notificationId = submit(request);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                mockMvc.perform(get("/api/v1/notifications/{id}", notificationId))
                        .andExpect(jsonPath("$.overallStatus", is("DELIVERED")))
                        .andExpect(jsonPath("$.selectedChannels[0]", is("EMAIL")))
                        .andExpect(jsonPath("$.recipientChannels[0].deliveryStatus", is("SUCCEEDED"))));

        mockMvc.perform(get("/api/v1/notifications/{id}/audit", notificationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action", is("NOTIFICATION_ACCEPTED")));
    }

    @Test
    void invalidSubmissionIsRejectedAndAudited() throws Exception {
        String body = "{\"sourceSystem\":\"trading-alerts\"}"; // missing required fields

        mockMvc.perform(post("/api/v1/notifications")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.rejectionReference").exists())
                .andExpect(jsonPath("$.fieldErrors").isNotEmpty());
    }

    @Test
    void malformedJsonWithInvalidEnumIsRejectedAndAudited() throws Exception {
        String body = "{\"sourceSystem\":\"trading-alerts\",\"eventId\":\"evt-1\","
                + "\"notificationType\":\"ALERT\",\"severity\":\"NOT_A_REAL_SEVERITY\","
                + "\"priority\":\"HIGH\",\"recipients\":[\"user-1\"],\"requestedChannels\":[\"EMAIL\"]}";

        mockMvc.perform(post("/api/v1/notifications")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.rejectionReference").exists())
                .andExpect(jsonPath("$.message", is("Request body is malformed or contains invalid field values")));
    }

    @Test
    void duplicateSubmissionWithSameIdempotencyKeyReturnsOriginalId() throws Exception {
        String idempotencyKey = "idem-" + UUID.randomUUID();
        NotificationRequest request = new NotificationRequest(
                "trading-alerts", UUID.randomUUID().toString(), NotificationType.ALERT,
                Severity.MEDIUM, Priority.NORMAL, List.of("user-1"), List.of(ChannelType.EMAIL),
                idempotencyKey, "subject", null, null);

        String firstId = submit(request);
        String secondId = submit(request);

        org.assertj.core.api.Assertions.assertThat(secondId).isEqualTo(firstId);
    }

    @Test
    void sameExplicitKeyFromDifferentSourceSystemsDoesNotCollide() throws Exception {
        String sharedKey = "shared-key-" + UUID.randomUUID();
        NotificationRequest fromSystemA = new NotificationRequest(
                "system-a", UUID.randomUUID().toString(), NotificationType.ALERT,
                Severity.MEDIUM, Priority.NORMAL, List.of("user-1"), List.of(ChannelType.EMAIL),
                sharedKey, "subject", null, null);
        NotificationRequest fromSystemB = new NotificationRequest(
                "system-b", UUID.randomUUID().toString(), NotificationType.ALERT,
                Severity.MEDIUM, Priority.NORMAL, List.of("user-1"), List.of(ChannelType.EMAIL),
                sharedKey, "subject", null, null);

        String idA = submit(fromSystemA);
        String idB = submit(fromSystemB);

        org.assertj.core.api.Assertions.assertThat(idB).isNotEqualTo(idA);
    }

    @Test
    void invalidRecipientFailsTerminallyWithoutRetry() throws Exception {
        NotificationRequest request = new NotificationRequest(
                "trading-alerts", UUID.randomUUID().toString(), NotificationType.ALERT,
                Severity.MEDIUM, Priority.NORMAL, List.of("invalid-user"), List.of(ChannelType.EMAIL),
                null, "subject", null, null);

        String notificationId = submit(request);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                mockMvc.perform(get("/api/v1/notifications/{id}", notificationId))
                        .andExpect(jsonPath("$.overallStatus", is("FAILED")))
                        .andExpect(jsonPath("$.recipientChannels[0].deliveryStatus", is("FAILED_TERMINAL")))
                        .andExpect(jsonPath("$.recipientChannels[0].attemptCount", is(1))));
    }

    @Test
    void flakyRecipientRetriesThenSucceeds() throws Exception {
        NotificationRequest request = new NotificationRequest(
                "trading-alerts", UUID.randomUUID().toString(), NotificationType.ALERT,
                Severity.MEDIUM, Priority.NORMAL, List.of("flaky-user"), List.of(ChannelType.SMS),
                null, "subject", null, null);

        String notificationId = submit(request);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                mockMvc.perform(get("/api/v1/notifications/{id}", notificationId))
                        .andExpect(jsonPath("$.overallStatus", is("DELIVERED")))
                        .andExpect(jsonPath("$.recipientChannels[0].attemptCount", is(3))));
    }

    @Test
    void webhookChannelDeliversSuccessfully() throws Exception {
        NotificationRequest request = new NotificationRequest(
                "ops-system", UUID.randomUUID().toString(), NotificationType.SYSTEM,
                Severity.LOW, Priority.LOW, List.of("user-3"), List.of(ChannelType.WEBHOOK),
                null, "subject", null, null);

        String notificationId = submit(request);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                mockMvc.perform(get("/api/v1/notifications/{id}", notificationId))
                        .andExpect(jsonPath("$.overallStatus", is("DELIVERED")))
                        .andExpect(jsonPath("$.recipientChannels[0].channel", is("WEBHOOK"))));
    }

    private String submit(NotificationRequest request) throws Exception {
        String body = mockMvc.perform(post("/api/v1/notifications")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("notificationId").asText();
    }
}
