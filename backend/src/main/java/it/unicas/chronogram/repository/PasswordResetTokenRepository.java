package it.unicas.chronogram.repository;

import it.unicas.chronogram.domain.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Integer> {

    Optional<PasswordResetToken> findBySelector(String selector);

    Optional<PasswordResetToken> findByUserId(Integer userId);

    void deleteBySelector(String selector);

    @Modifying
    @Query("delete from PasswordResetToken t where t.expirationTime < :now")
    int deleteExpired(LocalDateTime now);
}
