package it.unicas.chronogram.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Password-reset token stored with the selector/verifier pattern: the selector
 * is looked up directly (indexed) while only a hash of the verifier is stored,
 * so a database leak does not expose usable reset tokens.
 * Maps the {@code password_reset_tokens} table.
 */
@Entity
@Table(name = "password_reset_tokens")
@Getter
@Setter
@NoArgsConstructor
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "token_id")
    private Integer tokenId;

    @Column(name = "user_id", nullable = false, unique = true)
    private Integer userId;

    @Column(nullable = false, length = 64)
    private String selector;

    @Column(name = "verifier_hash", nullable = false)
    private String verifierHash;

    @Column(name = "expiration_time", nullable = false)
    private LocalDateTime expirationTime;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
