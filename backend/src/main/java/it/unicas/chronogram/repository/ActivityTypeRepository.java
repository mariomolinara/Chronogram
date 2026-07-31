package it.unicas.chronogram.repository;

import it.unicas.chronogram.domain.ActivityType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ActivityTypeRepository extends JpaRepository<ActivityType, Integer> {

    List<ActivityType> findAllByOrderByNameAsc();
}
