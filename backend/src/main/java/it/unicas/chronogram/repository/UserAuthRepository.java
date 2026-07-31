package it.unicas.chronogram.repository;

import it.unicas.chronogram.domain.UserAuth;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserAuthRepository extends JpaRepository<UserAuth, Integer> {

    Optional<UserAuth> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);
}
