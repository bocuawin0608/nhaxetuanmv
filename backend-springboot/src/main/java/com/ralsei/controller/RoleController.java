package com.ralsei.controller;

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

import com.ralsei.dto.request.role.CreateRoleRequest;
import com.ralsei.dto.request.role.RoleFilterRequest;
import com.ralsei.dto.request.role.UpdateRoleRequest;
import com.ralsei.dto.response.role.RoleDetailResponse;
import com.ralsei.dto.response.role.RoleListResponse;
import com.ralsei.service.RoleService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Admin", description = "Admin role management")
@RestController
@RequestMapping("/api/v1/admin/roles")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
/**
 * REST controller for managing role operations.
 * All endpoints require ADMIN role.
 */

/**
 * Handles HTTP requests for role operations.
 */
public class RoleController {
    private final RoleService roleService;

    @GetMapping(path = {"", "/"})
    public ResponseEntity<Page<RoleListResponse>> filterRoles(
        @Valid @ModelAttribute RoleFilterRequest filterRequest,
        Pageable pageable
    ) {
        return ResponseEntity.ok(roleService.filterRoles(filterRequest, pageable));
    }

    @GetMapping("/{roleId:\\d+}")
    public ResponseEntity<RoleDetailResponse> getRoleDetail(
        @PathVariable @Min(value = 1, message = "ID vai trò phải lớn hơn 0.") Integer roleId
    ) {
        return ResponseEntity.ok(roleService.getRoleDetail(roleId));
    }

    @PostMapping(path = {"", "/"})
    /**
     * Creates the role.
     *
     * @param request the value supplied for this operation
     *
     * @return the created role
     */
    public ResponseEntity<Integer> createRole(@Valid @RequestBody CreateRoleRequest request) {
        Integer newId = roleService.createRole(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(newId);
    }

    @PutMapping("/{roleId:\\d+}")
    public ResponseEntity<Void> updateRole(
        @PathVariable @Min(value = 1, message = "ID vai trò phải lớn hơn 0.") Integer roleId,
        @Valid @RequestBody UpdateRoleRequest request
    ) {
        roleService.updateRole(roleId, request);
        return ResponseEntity.ok().build();
    }

    @Hidden
    @Deprecated
    @PatchMapping("/{roleId:\\d+}/toggle-active")
    public ResponseEntity<Void> toggleActive(
        @PathVariable @Min(value = 1, message = "ID vai trò phải lớn hơn 0.") Integer roleId
    ) {
        roleService.toggleActive(roleId);
        return ResponseEntity.ok().build();
    }

    @Hidden
    @Deprecated
    @DeleteMapping("/{roleId:\\d+}")
    public ResponseEntity<Void> deleteRole(
        @PathVariable @Min(value = 1, message = "ID vai trò phải lớn hơn 0.") Integer roleId
    ) {
        roleService.deleteRole(roleId);
        return ResponseEntity.noContent().build();
    }
}
