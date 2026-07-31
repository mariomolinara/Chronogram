package it.unicas.chronogram.repository;

import it.unicas.chronogram.domain.Activity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ActivityRepository extends JpaRepository<Activity, Integer> {

    List<Activity> findByUserIdAndActivityDateOrderByCreatedAtAsc(Integer userId, LocalDate activityDate);

    Optional<Activity> findByIdAndUserId(Integer id, Integer userId);
}
