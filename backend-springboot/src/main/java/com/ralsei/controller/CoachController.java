package com.ralsei.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ralsei.dto.request.coach.CoachCreateRequest;
import com.ralsei.dto.request.coach.CoachFilterRequest;
import com.ralsei.dto.request.coach.CoachReactivateRequest;
import com.ralsei.dto.request.coach.CoachReportMaintenanceRequest;
import com.ralsei.dto.request.coach.CoachRetireRequest;
import com.ralsei.dto.request.coach.CoachUpdateInfoRequest;
import com.ralsei.dto.request.coach.CoachUpdateSeatsRequest;
import com.ralsei.dto.response.coach.CoachDetailResponse;
import com.ralsei.dto.response.coach.CoachResponse;
import com.ralsei.dto.response.coach.CoachStatusChangeCheckResponse;
import com.ralsei.dto.response.coach.CoachStatusLogResponse;
import com.ralsei.model.enums.CoachStatus;
import com.ralsei.service.CoachService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Coaches", description = "Coach fleet management")
@RestController
@RequestMapping("/api/v1/coaches")
@RequiredArgsConstructor
@Validated
/**
 * Handles HTTP requests for coach operations.
 */
public class CoachController {
    private final CoachService coachService;

    @GetMapping(path = {"", "/"})
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Page<CoachResponse>> filterCoaches(
        @Valid @ModelAttribute CoachFilterRequest filterRequest,
        Pageable pageable
    ) {
        return ResponseEntity.ok(coachService.filterCoaches(filterRequest, pageable));
    }

    @PostMapping(path = {"", "/"})
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    /**
     * Creates the coach.
     *
     * @param request the value supplied for this operation
     *
     * @return the created coach
     */
    public ResponseEntity<Integer> createCoach(@Valid @RequestBody CoachCreateRequest request) {
        Integer newId = coachService.createCoach(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(newId);
    }

    @GetMapping(path = {"/{id:\\d+}"})
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<CoachDetailResponse> getCoachDetail(
        @PathVariable @Min(value = 1, message = "ID của Xe phải lớn hơn 0.") Integer id
    ) {
        return ResponseEntity.ok(coachService.getCoachDetail(id));
    }

    @PutMapping(path = {"/{id:\\d+}"})
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Boolean> updateCoachInfo(
        @PathVariable @Min(value = 1, message = "ID của Xe phải lớn hơn 0.") Integer id,
        @Valid @RequestBody CoachUpdateInfoRequest request
    ) {
        return ResponseEntity.ok(coachService.updateCoachInfo(id, request));
    }

    @PatchMapping(path = {"/{id:\\d+}/seats"})
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Void> updateCoachSeats(
        @PathVariable @Min(value = 1, message = "ID của Xe phải lớn hơn 0.") Integer id,
        @Valid @RequestBody CoachUpdateSeatsRequest request
    ) {
        coachService.updateCoachSeats(id, request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping(path = {"/{id:\\d+}/status-change-check"})
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<CoachStatusChangeCheckResponse> getStatusChangeCheck(
        @PathVariable @Min(value = 1, message = "ID của Xe phải lớn hơn 0.") Integer id,
        @RequestParam CoachStatus target
    ) {
        return ResponseEntity.ok(coachService.getStatusChangeCheck(id, target));
    }

    @PostMapping(path = {"/{id:\\d+}/report-maintenance"})
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Void> reportMaintenance(
        @PathVariable @Min(value = 1, message = "ID của Xe phải lớn hơn 0.") Integer id,
        @Valid @RequestBody CoachReportMaintenanceRequest request
    ) {
        coachService.reportMaintenance(id, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = {"/{id:\\d+}/reactivate"})
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Void> reactivate(
        @PathVariable @Min(value = 1, message = "ID của Xe phải lớn hơn 0.") Integer id,
        @Valid @RequestBody CoachReactivateRequest request
    ) {
        coachService.reactivate(id, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = {"/{id:\\d+}/retire"})
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Void> retire(
        @PathVariable @Min(value = 1, message = "ID của Xe phải lớn hơn 0.") Integer id,
        @Valid @RequestBody CoachRetireRequest request
    ) {
        coachService.retire(id, request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping(path = {"/{id:\\d+}/status-logs"})
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Page<CoachStatusLogResponse>> getStatusLogs(
        @PathVariable @Min(value = 1, message = "ID của Xe phải lớn hơn 0.") Integer id,
        Pageable pageable
    ) {
        return ResponseEntity.ok(coachService.getStatusLogs(id, pageable));
    }
}
