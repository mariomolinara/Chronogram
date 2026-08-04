package it.unicas.chronogram.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One successful login. {@code user_auth.last_login} only keeps the latest
 * timestamp; this append-only history is what the admin dashboard aggregates to
 * answer "how many users were active in the last N days" and "who logged in on
 * every one of the last N days".
 */
@Entity
@Table(name = "login_event")
@Getter
@Setter
@NoArgsConstructor
public class LoginEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "login_event_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(name = "login_at", nullable = false)
    private LocalDateTime loginAt;

    public LoginEvent(Integer userId, LocalDateTime loginAt) {
        this.userId = userId;
        this.loginAt = loginAt;
    }
}
