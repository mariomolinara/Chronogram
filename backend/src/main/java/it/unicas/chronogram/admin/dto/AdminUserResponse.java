package it.unicas.chronogram.admin.dto;

import it.unicas.chronogram.domain.AccountStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One participant account as the back office sees it: who they are, what state
 * the account is in, and how much they have actually contributed - the last part
 * being what tells a dormant registration apart from an engaged participant.
 *
 * @param userId          database id, used as the target of the admin actions
 * @param email           login address
 * @param name            profile first name, null when the profile is incomplete
 * @param surname         profile last name, null when the profile is incomplete
 * @param status          lifecycle state (PENDING / ACTIVE / BLOCKED)
 * @param registeredAt    when the account was created
 * @param lastLogin       most recent successful sign-in, null if never
 * @param activityCount   activities logged so far
 * @param lastActivityDay date of the most recent activity, null if none
 */
public record AdminUserResponse(Integer userId,
                                String email,
                                String name,
                                String surname,
                                AccountStatus status,
                                LocalDateTime registeredAt,
                                LocalDateTime lastLogin,
                                long activityCount,
                                LocalDate lastActivityDay) {
}
