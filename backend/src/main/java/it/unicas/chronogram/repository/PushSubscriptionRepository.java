package it.unicas.chronogram.repository;

import it.unicas.chronogram.domain.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Integer> {

    /** Every device of one user: the recipients of a single reminder. */
    List<PushSubscription> findByUserId(Integer userId);

    long countByUserId(Integer userId);

    /**
     * Looked up without the user id on purpose: the endpoint is unique across the
     * table, and an endpoint a browser re-issued to another account has to be
     * found so it can be reassigned rather than rejected as a duplicate.
     */
    Optional<PushSubscription> findByEndpoint(String endpoint);
}
