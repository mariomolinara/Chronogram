package it.unicas.chronogram.repository;

import it.unicas.chronogram.domain.AccountStatus;
import it.unicas.chronogram.domain.Role;
import it.unicas.chronogram.domain.UserAuth;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /** Per-state totals for the back-office counters, administrators excluded. */
    long countByAccountStatusAndRoleNot(AccountStatus status, Role role);

    /**
     * One page of participant accounts for the back office, filtered by state
     * and by a free-text term matched against the email and the profile name.
     *
     * <p>The profile lives in another table with no JPA association, hence the
     * explicit entity join: doing the name filter in memory would make paging
     * and totals wrong. Administrators are excluded - they are operators, not
     * participants, and must not be actionable from this list.
     */
    @Query(value = """
            SELECT u FROM UserAuth u
            LEFT JOIN UserProfile p ON p.userId = u.userId
            WHERE u.role <> :adminRole
              AND (:status IS NULL OR u.accountStatus = :status)
              AND (:term IS NULL
                   OR LOWER(u.email) LIKE :term
                   OR LOWER(COALESCE(p.name, '')) LIKE :term
                   OR LOWER(COALESCE(p.surname, '')) LIKE :term)
            """,
            countQuery = """
                    SELECT COUNT(u) FROM UserAuth u
                    LEFT JOIN UserProfile p ON p.userId = u.userId
                    WHERE u.role <> :adminRole
                      AND (:status IS NULL OR u.accountStatus = :status)
                      AND (:term IS NULL
                           OR LOWER(u.email) LIKE :term
                           OR LOWER(COALESCE(p.name, '')) LIKE :term
                           OR LOWER(COALESCE(p.surname, '')) LIKE :term)
                    """)
    Page<UserAuth> search(@Param("adminRole") Role adminRole,
                          @Param("status") AccountStatus status,
                          @Param("term") String term,
                          Pageable pageable);
}
