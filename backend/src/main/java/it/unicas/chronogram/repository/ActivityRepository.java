package it.unicas.chronogram.repository;

import it.unicas.chronogram.domain.Activity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ActivityRepository extends JpaRepository<Activity, Integer> {

    List<Activity> findByUserIdAndActivityDateOrderByCreatedAtAsc(Integer userId, LocalDate activityDate);

    Optional<Activity> findByIdAndUserId(Integer id, Integer userId);

    /**
     * Number of logged activities per calendar day since {@code from}, oldest
     * first - the temporal distribution shown on the admin dashboard. Days with
     * no activity are absent and are filled in by the caller.
     */
    @Query("""
            SELECT a.activityDate AS day, COUNT(a) AS total
            FROM Activity a
            WHERE a.activityDate >= :from
            GROUP BY a.activityDate
            ORDER BY a.activityDate
            """)
    List<DailyCount> countPerDaySince(@Param("from") LocalDate from);

    /** Every activity, oldest first, for the CSV export. */
    List<Activity> findAllByOrderByActivityDateAscIdAsc();

    /**
     * How much each of the given users has logged, and when they last did.
     * Fetched in one query for the whole page of the user list rather than per
     * row, which would be a query per user.
     */
    @Query("""
            SELECT a.userId AS userId, COUNT(a) AS total, MAX(a.activityDate) AS lastDay
            FROM Activity a
            WHERE a.userId IN :userIds
            GROUP BY a.userId
            """)
    List<UserActivitySummary> summariseByUserIds(@Param("userIds") Collection<Integer> userIds);

    /** Projection for {@link #summariseByUserIds(Collection)}. */
    interface UserActivitySummary {
        Integer getUserId();

        long getTotal();

        LocalDate getLastDay();
    }
}
