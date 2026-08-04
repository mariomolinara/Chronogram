package it.unicas.chronogram.repository;

import it.unicas.chronogram.domain.Role;
import it.unicas.chronogram.domain.UserAuth;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserAuthRepository extends JpaRepository<UserAuth, Integer> {

    Optional<UserAuth> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    /**
     * The built-in administrator provisioned from configuration. At most one row
     * carries the system flag; {@code findFirst} keeps the query total even if a
     * database were hand-edited to contain more.
     */
    Optional<UserAuth> findFirstBySystemAccountTrue();

    long countByRole(Role role);

    /** All accounts, oldest first, for the CSV export. */
    List<UserAuth> findAllByOrderByUserIdAsc();
}
