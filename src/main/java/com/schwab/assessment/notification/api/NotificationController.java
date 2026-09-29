package com.schwab.assessment.notification.api;

import com.schwab.assessment.notification.api.dto.AuditEventResponse;
import com.schwab.assessment.notification.api.dto.NotificationRequest;
import com.schwab.assessment.notification.api.dto.NotificationStatusResponse;
import com.schwab.assessment.notification.api.dto.NotificationSubmissionResponse;
import com.schwab.assessment.notification.service.NotificationStatusService;
import com.schwab.assessment.notification.service.NotificationSubmissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications", description = "Submit notifications and query delivery status")
public class NotificationController {

    private final NotificationSubmissionService submissionService;
    private final NotificationStatusService statusService;

    public NotificationController(NotificationSubmissionService submissionService,
                                   NotificationStatusService statusService) {
        this.submissionService = submissionService;
        this.statusService = statusService;
    }

    @PostMapping
    @Operation(summary = "Submit a notification (requirement 4.1)")
    public ResponseEntity<NotificationSubmissionResponse> submit(@Valid @RequestBody NotificationRequest request) {
        NotificationSubmissionResponse response = submissionService.submit(request);
        HttpStatus status = response.duplicate() ? HttpStatus.OK : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status).body(response);
    }

    @GetMapping("/{notificationId}")
    @Operation(summary = "Retrieve notification status (requirement 4.2)")
    public ResponseEntity<NotificationStatusResponse> getStatus(@PathVariable String notificationId) {
        return ResponseEntity.ok(statusService.getStatus(notificationId));
    }

    @GetMapping("/{notificationId}/audit")
    @Operation(summary = "Retrieve audit history for a notification (requirement 4.9)")
    public ResponseEntity<List<AuditEventResponse>> getAuditHistory(@PathVariable String notificationId) {
        return ResponseEntity.ok(statusService.getAuditHistory(notificationId));
    }
}
