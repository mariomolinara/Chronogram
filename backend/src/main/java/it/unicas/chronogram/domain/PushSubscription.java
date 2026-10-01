package it.unicas.chronogram.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One browser (or Android WebView) that granted the notification permission and
 * handed us its Web Push subscription. Maps the {@code push_subscription} table.
 *
 * <p>The endpoint is unique across the whole table, not per user: a browser may
 * hand the very same endpoint to a second account signed in on the same machine,
 * and the row is then reassigned to the new owner. Keeping two owners for one
 * endpoint would deliver one user's reminders onto the other's screen.
 *
 * <p>{@link #p256dh} and {@link #auth} are the subscription's own key material,
 * used to encrypt the payload for that device only. They are stored verbatim
 * because they are what the push protocol requires, but they are never logged.
 */
@Entity
@Table(name = "push_subscription")
@Getter
@Setter
@NoArgsConstructor
public class PushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "user_auth_user_id", nullable = false)
    private Integer userId;

    @Column(name = "endpoint", nullable = false, unique = true, length = 500)
    private String endpoint;

    @Column(name = "p256dh", nullable = false)
    private String p256dh;

    @Column(name = "auth", nullable = false)
    private String auth;

    /** Informative only, so a user can tell their devices apart in the UI. */
    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
