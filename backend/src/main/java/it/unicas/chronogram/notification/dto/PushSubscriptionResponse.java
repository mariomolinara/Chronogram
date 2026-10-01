package it.unicas.chronogram.notification.dto;

/**
 * Acknowledgement of a stored subscription, carrying the new device count so the
 * screen can refresh it without a second round-trip to
 * {@code GET /api/notifications/settings}.
 */
public record PushSubscriptionResponse(long subscriptionCount) {
}
