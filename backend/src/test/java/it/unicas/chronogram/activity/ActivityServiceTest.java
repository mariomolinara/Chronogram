package it.unicas.chronogram.activity;

import it.unicas.chronogram.activity.dto.ActivityResponse;
import it.unicas.chronogram.activity.dto.ActivityTypeResponse;
import it.unicas.chronogram.activity.dto.CreateActivityRequest;
import it.unicas.chronogram.activity.dto.UpdateActivityRequest;
import it.unicas.chronogram.common.exception.ApiExceptions.ResourceNotFoundException;
import it.unicas.chronogram.common.exception.ApiExceptions.ValidationException;
import it.unicas.chronogram.domain.Activity;
import it.unicas.chronogram.domain.ActivityType;
import it.unicas.chronogram.repository.ActivityRepository;
import it.unicas.chronogram.repository.ActivityTypeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityServiceTest {

    @Mock private ActivityRepository activityRepository;
    @Mock private ActivityTypeRepository activityTypeRepository;

    @InjectMocks private ActivityService activityService;

    private ActivityType type(int id) {
        ActivityType t = new ActivityType();
        t.setId(id);
        t.setName("Work");
        t.setDescription("Working");
        t.setInstrumental(true);
        t.setRoutinary(false);
        return t;
    }

    // ---- create ----

    @Test
    void createPersistsActivityWithCurrentDateAndOwner() {
        when(activityTypeRepository.findById(3)).thenReturn(Optional.of(type(3)));
        when(activityRepository.save(any(Activity.class))).thenAnswer(inv -> {
            Activity a = inv.getArgument(0);
            a.setId(100);
            return a;
        });

        CreateActivityRequest req = new CreateActivityRequest(3, 60, 2, "Office", "12.50");
        ActivityResponse response = activityService.create(55, req);

        ArgumentCaptor<Activity> captor = ArgumentCaptor.forClass(Activity.class);
        verify(activityRepository).save(captor.capture());
        Activity saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(55);
        assertThat(saved.getActivityDate()).isEqualTo(LocalDate.now());
        assertThat(saved.getDurationMins()).isEqualTo(60);
        assertThat(saved.getCostEuro()).isEqualByComparingTo(new BigDecimal("12.50"));
        assertThat(saved.getActivityType().getId()).isEqualTo(3);

        assertThat(response.activityId()).isEqualTo(100);
        assertThat(response.activityTypeName()).isEqualTo("Work");
        assertThat(response.costEuro()).isEqualTo("12.50");
    }

    @Test
    void createRejectsUnknownActivityType() {
        when(activityTypeRepository.findById(999)).thenReturn(Optional.empty());

        CreateActivityRequest req = new CreateActivityRequest(999, 30, 0, null, null);
        assertThatThrownBy(() -> activityService.create(55, req))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Invalid activity type");
        verify(activityRepository, never()).save(any());
    }

    @Test
    void createRejectsNegativeCost() {
        when(activityTypeRepository.findById(3)).thenReturn(Optional.of(type(3)));

        CreateActivityRequest req = new CreateActivityRequest(3, 30, 0, null, "-5");
        assertThatThrownBy(() -> activityService.create(55, req))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("non-negative");
        verify(activityRepository, never()).save(any());
    }

    @Test
    void createRejectsNonNumericCost() {
        when(activityTypeRepository.findById(3)).thenReturn(Optional.of(type(3)));

        CreateActivityRequest req = new CreateActivityRequest(3, 30, 0, null, "abc");
        assertThatThrownBy(() -> activityService.create(55, req))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Invalid cost format");
    }

    @Test
    void createAllowsNullCost() {
        when(activityTypeRepository.findById(3)).thenReturn(Optional.of(type(3)));
        when(activityRepository.save(any(Activity.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateActivityRequest req = new CreateActivityRequest(3, 30, 0, null, null);
        ActivityResponse response = activityService.create(55, req);

        assertThat(response.costEuro()).isNull();
    }

    // ---- update ----

    private Activity ownedActivity() {
        Activity a = new Activity();
        a.setId(200);
        a.setUserId(55);
        a.setActivityType(type(3));
        a.setDurationMins(30);
        a.setPleasantness(0);
        return a;
    }

    @Test
    void updateAppliesOnlyNonNullFields() {
        Activity existing = ownedActivity();
        when(activityRepository.findByIdAndUserId(200, 55)).thenReturn(Optional.of(existing));
        when(activityRepository.save(any(Activity.class))).thenAnswer(inv -> inv.getArgument(0));

        // only duration is provided; the rest stay untouched
        UpdateActivityRequest req = new UpdateActivityRequest(200, null, 90, null, null, null);
        activityService.update(55, req);

        assertThat(existing.getDurationMins()).isEqualTo(90);
        assertThat(existing.getPleasantness()).isEqualTo(0);
        assertThat(existing.getActivityType().getId()).isEqualTo(3); // unchanged
    }

    @Test
    void updateChangesActivityTypeWhenProvided() {
        Activity existing = ownedActivity();
        when(activityRepository.findByIdAndUserId(200, 55)).thenReturn(Optional.of(existing));
        when(activityTypeRepository.findById(9)).thenReturn(Optional.of(type(9)));
        when(activityRepository.save(any(Activity.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateActivityRequest req = new UpdateActivityRequest(200, 9, null, null, null, null);
        activityService.update(55, req);

        assertThat(existing.getActivityType().getId()).isEqualTo(9);
    }

    @Test
    void updateFailsWhenActivityNotOwned() {
        when(activityRepository.findByIdAndUserId(200, 77)).thenReturn(Optional.empty());

        UpdateActivityRequest req = new UpdateActivityRequest(200, null, 90, null, null, null);
        assertThatThrownBy(() -> activityService.update(77, req))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(activityRepository, never()).save(any());
    }

    @Test
    void updateRejectsInvalidTypeWithoutSaving() {
        Activity existing = ownedActivity();
        when(activityRepository.findByIdAndUserId(200, 55)).thenReturn(Optional.of(existing));
        when(activityTypeRepository.findById(999)).thenReturn(Optional.empty());

        UpdateActivityRequest req = new UpdateActivityRequest(200, 999, null, null, null, null);
        assertThatThrownBy(() -> activityService.update(55, req))
                .isInstanceOf(ValidationException.class);
        verify(activityRepository, never()).save(any());
    }

    // ---- delete ----

    @Test
    void deleteRemovesOwnedActivity() {
        Activity existing = ownedActivity();
        when(activityRepository.findByIdAndUserId(200, 55)).thenReturn(Optional.of(existing));

        activityService.delete(55, 200);

        verify(activityRepository).delete(existing);
    }

    @Test
    void deleteFailsWhenNotOwned() {
        when(activityRepository.findByIdAndUserId(200, 77)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> activityService.delete(77, 200))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(activityRepository, never()).delete(any());
    }

    // ---- listByDate ----

    @Test
    void listByDateParsesIsoDate() {
        Activity a = ownedActivity();
        when(activityRepository.findByUserIdAndActivityDateOrderByCreatedAtAsc(55, LocalDate.of(2026, 7, 31)))
                .thenReturn(List.of(a));

        List<ActivityResponse> result = activityService.listByDate(55, "2026-07-31");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).activityId()).isEqualTo(200);
    }

    @Test
    void listByDateDefaultsToTodayWhenBlank() {
        when(activityRepository.findByUserIdAndActivityDateOrderByCreatedAtAsc(55, LocalDate.now()))
                .thenReturn(List.of());

        List<ActivityResponse> result = activityService.listByDate(55, "  ");

        assertThat(result).isEmpty();
        verify(activityRepository).findByUserIdAndActivityDateOrderByCreatedAtAsc(55, LocalDate.now());
    }

    @Test
    void listByDateRejectsInvalidDateFormat() {
        assertThatThrownBy(() -> activityService.listByDate(55, "31-07-2026"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("YYYY-MM-DD");
    }

    // ---- listTypes ----

    @Test
    void listTypesMapsToResponses() {
        when(activityTypeRepository.findAllByOrderByNameAsc()).thenReturn(List.of(type(3), type(9)));

        List<ActivityTypeResponse> result = activityService.listTypes();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("Work");
        assertThat(result.get(0).isInstrumental()).isTrue();
    }
}
