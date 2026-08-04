package it.unicas.chronogram.repository;

import it.unicas.chronogram.domain.LoginEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface LoginEventRepository extends JpaRepository<LoginEvent, Long> {

    /** Distinct accounts that logged in at least once since {@code since}. */
    @Query("SELECT COUNT(DISTINCT e.userId) FROM LoginEvent e WHERE e.loginAt >= :since")
    long countDistinctUsersSince(@Param("since") LocalDateTime since);

    /**
     * Accounts that logged in on at least {@code requiredDays} distinct calendar
     * days since {@code since} - the "regular user" metric. Native because the
     * day bucketing needs {@code DATE()}; the count of distinct days is compared
     * against the window length by the caller.
     */
    @Query(value = """
            SELECT COUNT(*) FROM (
                SELECT user_id
                FROM login_event
                WHERE login_at >= :since
                GROUP BY user_id
                HAVING COUNT(DISTINCT DATE(login_at)) >= :requiredDays
            ) AS regulars
            """, nativeQuery = true)
    long countUsersActiveOnAtLeastDistinctDays(@Param("since") LocalDateTime since,
                                               @Param("requiredDays") int requiredDays);
}
