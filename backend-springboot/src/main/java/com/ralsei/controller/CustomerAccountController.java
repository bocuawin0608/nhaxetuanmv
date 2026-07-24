package com.ralsei.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ralsei.dto.request.customer.CustomerProfileUpdateRequest;
import com.ralsei.dto.response.customer.CustomerAccountActionResponse;
import com.ralsei.dto.response.customer.CustomerProfileResponse;
import com.ralsei.service.CustomerAccountService;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Customer website account self-service endpoints.
 */
@Tag(name = "Customer", description = "Customer account self-service")
@RestController
@RequestMapping("/api/v1/customer/me")
@PreAuthorize("hasRole('CUSTOMER')")
@RequiredArgsConstructor
/**
 * Handles HTTP requests for customer account operations.
 */
public class CustomerAccountController {

    private final CustomerAccountService customerAccountService;

    /**
     * Returns the signed-in customer's profile.
     */
    @GetMapping
    public ResponseEntity<CustomerProfileResponse> getCurrentProfile() {
        return ResponseEntity.ok(customerAccountService.getCurrentProfile());
    }

    /**
     * Updates safe editable profile fields for the signed-in customer.
     */
    @PatchMapping
    public ResponseEntity<CustomerProfileResponse> updateCurrentProfile(
        @Valid @RequestBody CustomerProfileUpdateRequest request
    ) {
        return ResponseEntity.ok(customerAccountService.updateCurrentProfile(request));
    }

    /**
     * Soft-deactivates the signed-in customer account.
     */
    @Hidden
    @Deprecated
    @DeleteMapping
    /**
     * Executes the deactivate current account operation.
     *
     * @return the operation result
     */
    public ResponseEntity<CustomerAccountActionResponse> deactivateCurrentAccount() {
        return ResponseEntity.ok(customerAccountService.deactivateCurrentAccount());
    }
}
