package it.unicas.chronogram.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Authentication/credentials record for a user. Maps the {@code user_auth} table.
 */
@Entity
@Table(name = "user_auth")
@Getter
@Setter
@NoArgsConstructor
public class UserAuth {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Integer userId;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    /**
     * Derived from {@link #accountStatus} and never set on its own - see
     * {@link #setStatus(AccountStatus)}. It stays the single flag consulted by
     * the JWT filter and the password-reset flow, which is what makes blocking
     * an account revoke the tokens already issued to it.
     */
    @Setter(AccessLevel.NONE)
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 20)
    private AccountStatus accountStatus = AccountStatus.ACTIVE;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts = 0;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role = Role.USER;

    /**
     * True for the built-in administrator provisioned from configuration. Such an
     * account must never be deleted, deactivated or demoted: only its email and
     * password can change.
     */
    @Column(name = "is_system", nullable = false)
    private boolean systemAccount = false;

    /** Set while the account still uses the password it was provisioned with. */
    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword = false;

    /**
     * The only way to change the lifecycle state. Keeping {@code is_active} in
     * sync here - rather than at each call site - is what guarantees the two can
     * never disagree, which would otherwise let a blocked account keep using a
     * token it was issued before being blocked.
     */
    public void setStatus(AccountStatus status) {
        this.accountStatus = status;
        this.active = status.canAuthenticate();
    }

    /** True if the account is currently within a lockout window. */
    public boolean isLocked(Instant now) {
        return lockedUntil != null
                && lockedUntil.isAfter(LocalDateTime.ofInstant(now, java.time.ZoneId.systemDefault()));
    }
}
