package it.unicas.chronogram.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * What a user asked of the periodic push reminders, plus the single piece of
 * bookkeeping the scheduler needs ({@link #getLastSentAt()}). Maps the
 * {@code notification_preference} table, whose primary key
 * {@code user_auth_user_id} is shared with {@link UserAuth#getUserId()} - the
 * same one-to-one shape {@link UserProfile} uses.
 *
 * <p>The row is created lazily: a user who never opened the notification screen
 * has none, and the API answers with the defaults declared here rather than
 * writing a row just to read it back.
 */
@Entity
@Table(name = "notification_preference")
@Getter
@Setter
@NoArgsConstructor
public class NotificationPreference {

    /**
     * The zone every timestamp on this row - and the silent window - is expressed
     * in. Explicit rather than the JVM default, because the same WAR runs in a
     * container whose clock is UTC and "quiet from 22:00" has to mean the user's
     * 22:00; and because {@code lastSentAt} is written by the API and read back by
     * the scheduler, so the two must agree on what hour it is or the first reminder
     * would land an offset early. Matches the zone the datasource and Hibernate are
     * pinned to in {@code application.yml}.
     */
    public static final ZoneId ZONE = ZoneId.of("Europe/Rome");

    /** Default cadence, in minutes, for a user who has not chosen one. */
    public static final int DEFAULT_INTERVAL_MINUTES = 60;
    /** Default silent window: 22:00 until 08:00, Europe/Rome. */
    public static final int DEFAULT_QUIET_HOURS_START = 22;
    public static final int DEFAULT_QUIET_HOURS_END = 8;

    @Id
    @Column(name = "user_auth_user_id")
    private Integer userId;

    @Column(name = "push_enabled", nullable = false)
    private boolean pushEnabled = false;

    @Column(name = "interval_minutes", nullable = false)
    private int intervalMinutes = DEFAULT_INTERVAL_MINUTES;

    /**
     * Hour (0-23) the silent window opens; {@code null} - together with
     * {@link #quietHoursEnd} - means the user wants no silent window.
     *
     * <p>Held as an {@code Integer} because that is what the API speaks, while
     * the column is a {@code TINYINT}: the explicit {@code @JdbcTypeCode} is what
     * keeps the two in agreement, since {@code ddl-auto: validate} compares the
     * mapped JDBC type against the one the database reports and would otherwise
     * refuse to start.
     */
    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "quiet_hours_start")
    private Integer quietHoursStart = DEFAULT_QUIET_HOURS_START;

    /** Hour (0-23) the silent window closes. See {@link #quietHoursStart}. */
    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "quiet_hours_end")
    private Integer quietHoursEnd = DEFAULT_QUIET_HOURS_END;

    /**
     * When the scheduler last sent a reminder to this user, or {@code null} if it
     * never did - which makes the user due at once, hence the stamp applied when
     * the reminders are switched on.
     */
    @Column(name = "last_sent_at")
    private LocalDateTime lastSentAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Whether a reminder is owed at {@code now}: the reminders are on and either
     * none has ever been sent or a full interval has elapsed since the last one.
     *
     * <p>Lives on the entity rather than in the scheduler so the one definition of
     * "due" is the one the tests exercise, and so the scheduler's database query
     * stays a plain "give me the enabled ones" - the per-row interval cannot be
     * expressed as a derived query anyway.
     */
    public boolean isDueAt(LocalDateTime now) {
        if (!pushEnabled) {
            return false;
        }
        return lastSentAt == null
                || !lastSentAt.plusMinutes(intervalMinutes).isAfter(now);
    }

    /**
     * Whether {@code now} falls inside the silent window, the window being
     * half-open on the hour: {@code [start, end)}.
     *
     * <p>Handles the wrap-around case that is also the default - 22 to 8 covers
     * 22:00-23:59 <em>and</em> 00:00-07:59 - and treats a zero-length window
     * ({@code start == end}) as no window at all rather than as a permanent
     * silence, which would switch the feature off by accident.
     */
    public boolean isQuietAt(LocalDateTime now) {
        if (quietHoursStart == null || quietHoursEnd == null) {
            return false;
        }
        int start = quietHoursStart;
        int end = quietHoursEnd;
        if (start == end) {
            return false;
        }
        int hour = now.getHour();
        return start < end
                ? hour >= start && hour < end
                : hour >= start || hour < end;
    }
}
