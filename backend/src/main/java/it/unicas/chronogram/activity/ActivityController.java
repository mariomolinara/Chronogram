package it.unicas.chronogram.activity;

import it.unicas.chronogram.activity.dto.*;
import it.unicas.chronogram.common.ApiResponse;
import it.unicas.chronogram.security.AuthPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * JWT-protected activity endpoints. The authenticated user is injected via
 * {@link AuthPrincipal}; the client can never spoof another user's id.
 */
@RestController
@RequestMapping("/api/activities")
public class ActivityController {

    private final ActivityService activityService;

    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    @PostMapping("/create")
    public ApiResponse<ActivityResponse> create(@AuthenticationPrincipal AuthPrincipal principal,
                                                @Valid @RequestBody CreateActivityRequest request) {
        ActivityResponse created = activityService.create(principal.userId(), request);
        return ApiResponse.ok("Activity created successfully", created);
    }

    @PostMapping("/update")
    public ApiResponse<ActivityResponse> update(@AuthenticationPrincipal AuthPrincipal principal,
                                                @Valid @RequestBody UpdateActivityRequest request) {
        ActivityResponse updated = activityService.update(principal.userId(), request);
        return ApiResponse.ok("Activity updated successfully", updated);
    }

    @PostMapping("/delete")
    public ApiResponse<Void> delete(@AuthenticationPrincipal AuthPrincipal principal,
                                    @Valid @RequestBody DeleteActivityRequest request) {
        activityService.delete(principal.userId(), request.activityId());
        return ApiResponse.ok("Activity deleted successfully");
    }

    @RequestMapping(value = "/list", method = {RequestMethod.POST, RequestMethod.GET})
    public ApiResponse<List<ActivityResponse>> list(@AuthenticationPrincipal AuthPrincipal principal,
                                                    @RequestBody(required = false) ListActivitiesRequest request) {
        String date = request != null ? request.activityDate() : null;
        List<ActivityResponse> activities = activityService.listByDate(principal.userId(), date);
        return ApiResponse.ok("Attività recuperate con successo", activities);
    }

    @RequestMapping(value = "/types", method = {RequestMethod.POST, RequestMethod.GET})
    public ApiResponse<List<ActivityTypeResponse>> types() {
        return ApiResponse.ok("Activity types retrieved successfully", activityService.listTypes());
    }
}
