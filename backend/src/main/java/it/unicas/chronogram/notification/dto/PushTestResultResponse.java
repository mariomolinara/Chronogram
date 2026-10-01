package it.unicas.chronogram.notification.dto;

/**
 * Outcome of the "send me one now" button, per device.
 *
 * <p>A partial result is normal and is reported as such rather than as an error:
 * one of the user's browsers may have discarded the subscription while another is
 * perfectly reachable, and the screen should say "1 of 2" instead of failing.
 *
 * @param sent   devices the push service accepted the notification for
 * @param failed devices it refused, including the subscriptions found to be
 *               expired - those are dropped from the database as a side effect
 */
public record PushTestResultResponse(int sent, int failed) {
}
