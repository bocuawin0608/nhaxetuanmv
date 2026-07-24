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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ralsei.dto.request.staff.OnboardStaffRequest;
import com.ralsei.dto.request.staff.StaffFilterRequest;
import com.ralsei.dto.request.staff.UpdateStaffRequest;
import com.ralsei.dto.response.staff.OnboardStaffResponse;
import com.ralsei.dto.response.staff.StaffDetailResponse;
import com.ralsei.dto.response.staff.StaffListResponse;
import com.ralsei.service.StaffService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Admin", description = "Admin staff management")
@RestController
@RequestMapping("/api/v1/admin/staff")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
/**
 * REST controller for managing staff operations.
 * All endpoints require ADMIN role.
 */

/**
 * Handles HTTP requests for staff operations.
 */
public class StaffController {
    private final StaffService staffService;

    @GetMapping(path = {"", "/"})
    public ResponseEntity<Page<StaffListResponse>> filterStaff(
        @Valid @ModelAttribute StaffFilterRequest filterRequest,
        Pageable pageable
    ) {
        return ResponseEntity.ok(staffService.filterStaff(filterRequest, pageable));
    }

    @GetMapping("/{staffId:\\d+}")
    public ResponseEntity<StaffDetailResponse> getStaffDetail(
        @PathVariable @Min(value = 1, message = "ID nhân viên phải lớn hơn 0.") Integer staffId
    ) {
        return ResponseEntity.ok(staffService.getStaffDetail(staffId));
    }

    @PostMapping("/onboard")
    /**
     * Executes the onboard staff operation.
     *
     * @param request the value supplied for this operation
     *
     * @return the operation result
     */
    public ResponseEntity<OnboardStaffResponse> onboardStaff(@Valid @RequestBody OnboardStaffRequest request) {
        OnboardStaffResponse response = staffService.onboardStaff(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{staffId:\\d+}")
    public ResponseEntity<Void> updateStaff(
        @PathVariable @Min(value = 1, message = "ID nhân viên phải lớn hơn 0.") Integer staffId,
        @Valid @RequestBody UpdateStaffRequest request
    ) {
        staffService.updateStaff(staffId, request);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{staffId:\\d+}/toggle-active")
    public ResponseEntity<Void> toggleActive(
        @PathVariable @Min(value = 1, message = "ID nhân viên phải lớn hơn 0.") Integer staffId
    ) {
        staffService.toggleActive(staffId);
        return ResponseEntity.ok().build();
    }

    @Hidden
    @Deprecated
    @DeleteMapping("/{staffId:\\d+}")
    public ResponseEntity<Void> deleteStaff(
        @PathVariable @Min(value = 1, message = "ID nhân viên phải lớn hơn 0.") Integer staffId
    ) {
        staffService.deleteStaff(staffId);
        return ResponseEntity.noContent().build();
    }
}
