package it.unicas.chronogram.notification;

import it.unicas.chronogram.common.ApiResponse;
import it.unicas.chronogram.notification.dto.DeletePushSubscriptionRequest;
import it.unicas.chronogram.notification.dto.NotificationPreferencesRequest;
import it.unicas.chronogram.notification.dto.NotificationPreferencesResponse;
import it.unicas.chronogram.notification.dto.NotificationSettingsResponse;
import it.unicas.chronogram.notification.dto.PushSubscriptionRequest;
import it.unicas.chronogram.notification.dto.PushSubscriptionResponse;
import it.unicas.chronogram.notification.dto.PushTestResultResponse;
import it.unicas.chronogram.security.AuthPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * JWT-protected self-service endpoints for the periodic push reminders: the
 * user's own settings and the browsers they want to be reminded on. The subject
 * is always the authenticated {@link AuthPrincipal}, never an id taken from the
 * path or the body, so these routes cannot be pointed at somebody else's devices.
 *
 * <p>Everything is POST rather than PUT/DELETE, to match the rest of this API.
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * Whether this installation can send push at all, the public VAPID key the
     * browser needs to subscribe, the caller's settings and how many of their
     * devices are registered - one call, because the screen needs all four before
     * it can render anything.
     */
    @GetMapping("/settings")
    public ApiResponse<NotificationSettingsResponse> settings(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.ok("Notification settings retrieved successfully",
                notificationService.settings(principal.userId()));
    }

    /**
     * Stores the caller's settings and echoes back what was kept, so the client
     * renders the server's state rather than what it hoped to send.
     */
    @PostMapping("/preferences")
    public ApiResponse<NotificationPreferencesResponse> savePreferences(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody NotificationPreferencesRequest request) {
        return ApiResponse.ok("Notification preferences saved successfully",
                notificationService.savePreferences(principal.userId(), request));
    }

    /**
     * Registers the browser's push subscription, or updates it if the endpoint is
     * already on file. The answer carries the new device count so the screen can
     * refresh it without re-reading the settings.
     */
    @PostMapping("/subscriptions")
    public ApiResponse<PushSubscriptionResponse> subscribe(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody PushSubscriptionRequest request) {
        long count = notificationService.saveSubscription(principal.userId(), request);
        return ApiResponse.ok("Push subscription registered successfully",
                new PushSubscriptionResponse(count));
    }

    /**
     * Forgets a subscription. Idempotent: an endpoint that is not on file - or is
     * not the caller's - is an acknowledged no-op, because the client calls this
     * exactly when it can no longer be sure of the state.
     */
    @PostMapping("/subscriptions/delete")
    public ApiResponse<Void> unsubscribe(@AuthenticationPrincipal AuthPrincipal principal,
                                         @Valid @RequestBody DeletePushSubscriptionRequest request) {
        notificationService.deleteSubscription(principal.userId(), request.endpoint());
        return ApiResponse.ok("Push subscription removed successfully");
    }

    /**
     * Sends one notification to every registered device of the caller, at once:
     * neither the interval nor the silent window applies, and the next scheduled
     * reminder is not postponed. Answers 503 when the server has no VAPID keys.
     */
    @PostMapping("/test")
    public ApiResponse<PushTestResultResponse> test(@AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.ok("Test notification processed",
                notificationService.sendTest(principal.userId()));
    }
}
