package it.unicas.chronogram.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A Web Push subscription as the browser produced it. The owner is taken from
 * the JWT, never from the body.
 *
 * <p>The {@code @Size} bounds mirror the {@code push_subscription} columns, so an
 * oversized value comes back as a readable 400 instead of a database error.
 */
public record PushSubscriptionRequest(

        @NotBlank(message = "The subscription endpoint is required")
        @Size(max = 500, message = "The subscription endpoint must be at most 500 characters long")
        String endpoint,

        /** The subscription's public key, base64url, as {@code keys.p256dh}. */
        @NotBlank(message = "The subscription public key (p256dh) is required")
        @Size(max = 255, message = "The subscription public key must be at most 255 characters long")
        String p256dh,

        /** The subscription's auth secret, base64url, as {@code keys.auth}. */
        @NotBlank(message = "The subscription auth secret is required")
        @Size(max = 255, message = "The subscription auth secret must be at most 255 characters long")
        String auth,

        /** Optional, informative: lets the user tell their devices apart. */
        @Size(max = 255, message = "The user agent must be at most 255 characters long")
        String userAgent
) {
}
