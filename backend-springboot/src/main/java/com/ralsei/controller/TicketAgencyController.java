package com.ralsei.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ralsei.dto.request.ticketagency.CreateTicketAgencyRequest;
import com.ralsei.dto.request.ticketagency.TicketAgencyFilterRequest;
import com.ralsei.dto.request.ticketagency.UpdateTicketAgencyRequest;
import com.ralsei.dto.response.ticketagency.CoachStopDropdownDTO;
import com.ralsei.dto.response.ticketagency.TicketAgencyDetailResponse;
import com.ralsei.dto.response.ticketagency.TicketAgencyListResponse;
import com.ralsei.service.TicketAgencyService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Admin", description = "Admin ticket agency management")
@RestController
@RequestMapping("/api/v1/admin/ticket-agencies")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
/**
 * REST controller for managing ticketagency operations.
 * All endpoints require ADMIN role.
 */

/**
 * Handles HTTP requests for ticket agency operations.
 */
public class TicketAgencyController {
    private final TicketAgencyService ticketAgencyService;

    @GetMapping(path = {"", "/"})
    public ResponseEntity<Page<TicketAgencyListResponse>> filterTicketAgencies(
        @Valid @ModelAttribute TicketAgencyFilterRequest filterRequest,
        Pageable pageable
    ) {
        return ResponseEntity.ok(ticketAgencyService.filterTicketAgencies(filterRequest, pageable));
    }

    @GetMapping("/{ticketAgencyId:\\d+}")
    public ResponseEntity<TicketAgencyDetailResponse> getTicketAgencyDetail(
        @PathVariable @Min(value = 1, message = "ID bến xe phải lớn hơn 0.") Integer ticketAgencyId
    ) {
        return ResponseEntity.ok(ticketAgencyService.getTicketAgencyDetail(ticketAgencyId));
    }

    @PostMapping(path = {"", "/"})
    /**
     * Creates the ticket agency.
     *
     * @param request the value supplied for this operation
     *
     * @return the created ticket agency
     */
    public ResponseEntity<Integer> createTicketAgency(@Valid @RequestBody CreateTicketAgencyRequest request) {
        Integer newId = ticketAgencyService.createTicketAgency(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(newId);
    }

    @PutMapping("/{ticketAgencyId:\\d+}")
    public ResponseEntity<Void> updateTicketAgency(
        @PathVariable @Min(value = 1, message = "ID bến xe phải lớn hơn 0.") Integer ticketAgencyId,
        @Valid @RequestBody UpdateTicketAgencyRequest request
    ) {
        ticketAgencyService.updateTicketAgency(ticketAgencyId, request);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{ticketAgencyId:\\d+}/toggle-active")
    public ResponseEntity<Void> toggleActive(
        @PathVariable @Min(value = 1, message = "ID bến xe phải lớn hơn 0.") Integer ticketAgencyId
    ) {
        ticketAgencyService.toggleActive(ticketAgencyId);
        return ResponseEntity.ok().build();
    }

    @Hidden
    @Deprecated
    @DeleteMapping("/{ticketAgencyId:\\d+}")
    public ResponseEntity<Void> deleteTicketAgency(
        @PathVariable @Min(value = 1, message = "ID bến xe phải lớn hơn 0.") Integer ticketAgencyId
    ) {
        ticketAgencyService.deleteTicketAgency(ticketAgencyId);
        return ResponseEntity.noContent().build();
    }

    @Hidden
    @Deprecated
    @GetMapping("/coach-stop-dropdown")
    public ResponseEntity<List<CoachStopDropdownDTO>> getCoachStopDropdown() {
        return ResponseEntity.ok(ticketAgencyService.getCoachStopDropdown());
    }

    @GetMapping("/available-stops")
    public ResponseEntity<List<CoachStopDropdownDTO>> getAvailableStops() {
        return ResponseEntity.ok(ticketAgencyService.getAvailableStops());
    }
}
