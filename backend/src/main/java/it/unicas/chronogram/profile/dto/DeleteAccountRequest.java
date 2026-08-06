package it.unicas.chronogram.profile.dto;

import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Objects;

/**
 * Self-service deletion of one's own account. The subject is never part of the
 * payload: it is resolved from the JWT, so this request cannot be pointed at
 * another account.
 *
 * <p>The reasons are the checkboxes ticked on the confirmation screen and are
 * entirely optional - the field may be absent, null or empty, and the whole body
 * may be missing. Deleting an account is the user's right, not something they
 * have to justify, so a missing reason can never be a reason to refuse.
 *
 * @param reasons why the user is leaving, as chosen on the client; kept only in
 *                the server log (see {@code ProfileService.deleteAccount})
 */
public record DeleteAccountRequest(
        @Size(max = 20, message = "At most 20 reasons may be submitted")
        List<@Size(max = 200, message = "A reason may be at most 200 characters long") String> reasons
) {

    /**
     * The reasons as they should be logged: never null, trimmed, with nulls and
     * blanks dropped. A client that sends {@code ["", null]} has said nothing,
     * and the log should show exactly that instead of empty quotes.
     */
    public List<String> cleanedReasons() {
        if (reasons == null) {
            return List.of();
        }
        return reasons.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(reason -> !reason.isEmpty())
                .toList();
    }
}