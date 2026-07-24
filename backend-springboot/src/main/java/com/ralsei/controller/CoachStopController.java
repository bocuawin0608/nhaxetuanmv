package com.ralsei.controller;

import com.ralsei.dto.request.CoachAndRouteStop.CoachStopRequest;
import com.ralsei.dto.response.PagedResponse;
import com.ralsei.dto.response.CoachAndRouteStop.CoachStopResponse;
import com.ralsei.service.CoachStopService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Coaches", description = "Coach stop management")
@RestController
@RequestMapping("/api/v1/coach-stops")
@RequiredArgsConstructor
/**
 * Handles HTTP requests for coach stop operations.
 */
public class CoachStopController {

    private final CoachStopService coachStopService;

    @PostMapping
    /**
     * Creates the coach stop.
     *
     * @param request the value supplied for this operation
     *
     * @return the created coach stop
     */
    public ResponseEntity<CoachStopResponse> createCoachStop(@Valid @RequestBody CoachStopRequest request) {
        CoachStopResponse response = coachStopService.createCoachStop(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CoachStopResponse> updateCoachStop(
            @PathVariable int id,
            @Valid @RequestBody CoachStopRequest request) {
        CoachStopResponse response = coachStopService.updateCoachStop(id, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    /**
     * Returns the coach stop by id.
     *
     * @param id the value supplied for this operation
     *
     * @return the coach stop by id
     */
    public ResponseEntity<CoachStopResponse> getCoachStopById(@PathVariable int id) {
        CoachStopResponse response = coachStopService.getCoachStopById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<PagedResponse<CoachStopResponse>> getAllCoachStops(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PagedResponse<CoachStopResponse> response = coachStopService.getAllCoachStops(search, isActive, page, size);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/soft-delete")
    /**
     * Executes the soft delete coach stop operation.
     *
     * @param id the value supplied for this operation
     *
     * @return the operation result
     */
    public ResponseEntity<Void> softDeleteCoachStop(@PathVariable int id) {
        coachStopService.softDeleteCoachStop(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/restore")
    /**
     * Executes the restore coach stop operation.
     *
     * @param id the value supplied for this operation
     *
     * @return the operation result
     */
    public ResponseEntity<Void> restoreCoachStop(@PathVariable int id) {
        coachStopService.restoreCoachStop(id);
        return ResponseEntity.noContent().build();
    }
}
