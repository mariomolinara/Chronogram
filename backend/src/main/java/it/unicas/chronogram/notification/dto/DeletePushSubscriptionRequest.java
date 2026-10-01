package it.unicas.chronogram.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The endpoint of the subscription to forget - what the browser hands back when
 * the user revokes the permission or the app unsubscribes.
 *
 * <p>POST with a body rather than DELETE with the endpoint in the path, to match
 * the rest of this API and because a push endpoint is a full URL that has no
 * business being path-encoded.
 */
public record DeletePushSubscriptionRequest(

        @NotBlank(message = "The subscription endpoint is required")
        @Size(max = 500, message = "The subscription endpoint must be at most 500 characters long")
        String endpoint
) {
}
