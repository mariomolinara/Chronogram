package it.unicas.chronogram.repository;

import it.unicas.chronogram.domain.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface UserProfileRepository extends JpaRepository<UserProfile, Integer> {

    /** Profiles for one page of the back-office user list, in a single query. */
    List<UserProfile> findByUserIdIn(Collection<Integer> userIds);
}
