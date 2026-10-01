package it.unicas.chronogram.repository;

import it.unicas.chronogram.domain.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Integer> {

    /**
     * Every user who has the reminders switched on, which is the whole candidate
     * set the scheduler looks at each minute.
     *
     * <p>Whether a candidate is actually <em>due</em> depends on its own
     * {@code interval_minutes} compared against its own {@code last_sent_at} -
     * per-row date arithmetic that no derived query can express - so that step
     * happens in {@link NotificationPreference#isDueAt} instead. The set narrowed
     * here is small by construction (only the users who opted in), and the
     * alternative, hand-written date arithmetic in HQL, would trade a cheap
     * in-memory filter for a query that can only fail at startup.
     */
    List<NotificationPreference> findByPushEnabledTrue();
}
