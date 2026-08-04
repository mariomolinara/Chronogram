package it.unicas.chronogram.admin.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Aggregate figures behind the admin dashboard. Contains no personal data: only
 * counts and a per-day series.
 *
 * @param totalUsers        registered accounts, excluding the built-in administrator
 * @param activeUsers       accounts with at least one login in the last {@code activeWindowDays}
 * @param regularUsers      accounts that logged in on every one of the last {@code regularWindowDays}
 * @param totalActivities   activity records collected since the beginning
 * @param activitiesLastWeek activity records logged in the last 7 days
 * @param dailyActivities   activities per calendar day over the distribution window, gaps filled with zeros
 */
public record AdminStatsResponse(long totalUsers,
                                 long activeUsers,
                                 long regularUsers,
                                 long totalActivities,
                                 long activitiesLastWeek,
                                 int activeWindowDays,
                                 int regularWindowDays,
                                 List<DailyPoint> dailyActivities) {

    /** One bucket of the temporal distribution. */
    public record DailyPoint(LocalDate day, long count) {
    }
}
