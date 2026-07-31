package it.unicas.chronogram.activity;

import it.unicas.chronogram.activity.dto.*;
import it.unicas.chronogram.common.exception.ApiExceptions.ResourceNotFoundException;
import it.unicas.chronogram.common.exception.ApiExceptions.ValidationException;
import it.unicas.chronogram.domain.Activity;
import it.unicas.chronogram.domain.ActivityType;
import it.unicas.chronogram.repository.ActivityRepository;
import it.unicas.chronogram.repository.ActivityTypeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Business logic for activity CRUD and the activity-type catalog. Ownership is
 * always enforced against the authenticated user id.
 */
@Service
public class ActivityService {

    private static final Logger log = LoggerFactory.getLogger(ActivityService.class);

    private final ActivityRepository activityRepository;
    private final ActivityTypeRepository activityTypeRepository;

    public ActivityService(ActivityRepository activityRepository,
                           ActivityTypeRepository activityTypeRepository) {
        this.activityRepository = activityRepository;
        this.activityTypeRepository = activityTypeRepository;
    }

    @Transactional
    public ActivityResponse create(Integer userId, CreateActivityRequest req) {
        ActivityType type = requireActivityType(req.activityTypeId());
        LocalDateTime now = LocalDateTime.now();

        Activity activity = new Activity();
        activity.setUserId(userId);
        activity.setActivityType(type);
        activity.setActivityDate(LocalDate.now());
        activity.setDurationMins(req.durationMins());
        activity.setPleasantness(req.pleasantness());
        activity.setLocation(req.location());
        activity.setCostEuro(parseCost(req.costEuro()));
        activity.setCreatedAt(now);
        activity.setUpdatedAt(now);

        Activity saved = activityRepository.save(activity);
        log.info("Activity created with id={} for user_id={}", saved.getId(), userId);
        return ActivityResponse.from(saved);
    }

    @Transactional
    public ActivityResponse update(Integer userId, UpdateActivityRequest req) {
        Activity activity = activityRepository.findByIdAndUserId(req.activityId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Activity not found or access denied."));

        if (req.activityTypeId() != null) {
            activity.setActivityType(requireActivityType(req.activityTypeId()));
        }
        if (req.durationMins() != null) {
            activity.setDurationMins(req.durationMins());
        }
        if (req.pleasantness() != null) {
            activity.setPleasantness(req.pleasantness());
        }
        if (req.location() != null) {
            activity.setLocation(req.location());
        }
        if (req.costEuro() != null) {
            activity.setCostEuro(parseCost(req.costEuro()));
        }
        activity.setUpdatedAt(LocalDateTime.now());

        Activity saved = activityRepository.save(activity);
        log.info("Activity id={} updated for user_id={}", saved.getId(), userId);
        return ActivityResponse.from(saved);
    }

    @Transactional
    public void delete(Integer userId, Integer activityId) {
        Activity activity = activityRepository.findByIdAndUserId(activityId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Activity not found or access denied."));
        activityRepository.delete(activity);
        log.info("Activity id={} deleted for user_id={}", activityId, userId);
    }

    @Transactional(readOnly = true)
    public List<ActivityResponse> listByDate(Integer userId, String activityDate) {
        LocalDate date = parseDate(activityDate);
        return activityRepository.findByUserIdAndActivityDateOrderByCreatedAtAsc(userId, date)
                .stream()
                .map(ActivityResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ActivityTypeResponse> listTypes() {
        return activityTypeRepository.findAllByOrderByNameAsc()
                .stream()
                .map(ActivityTypeResponse::from)
                .toList();
    }

    private ActivityType requireActivityType(Integer activityTypeId) {
        return activityTypeRepository.findById(activityTypeId)
                .orElseThrow(() -> new ValidationException("Invalid activity type ID: " + activityTypeId));
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new ValidationException("Invalid date format. Expected format: YYYY-MM-DD.");
        }
    }

    private BigDecimal parseCost(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            BigDecimal cost = new BigDecimal(value.trim());
            if (cost.signum() < 0) {
                throw new ValidationException("Cost must be non-negative.");
            }
            return cost;
        } catch (NumberFormatException e) {
            throw new ValidationException("Invalid cost format.");
        }
    }
}
