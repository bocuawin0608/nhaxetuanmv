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

import com.ralsei.dto.request.customer.CreateCustomerRequest;
import com.ralsei.dto.request.customer.CustomerFilterRequest;
import com.ralsei.dto.request.customer.UpdateCustomerRequest;
import com.ralsei.dto.response.customer.CustomerDetailResponse;
import com.ralsei.dto.response.customer.CustomerListResponse;
import com.ralsei.service.CustomerService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Admin", description = "Admin customer management")
@RestController
@RequestMapping("/api/v1/admin/customers")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
/**
 * REST controller for managing customer operations.
 * All endpoints require ADMIN role.
 */

/**
 * Handles HTTP requests for customer operations.
 */
public class CustomerController {
    private final CustomerService customerService;

    @GetMapping(path = {"", "/"})
    public ResponseEntity<Page<CustomerListResponse>> filterCustomers(
        @Valid @ModelAttribute CustomerFilterRequest filterRequest,
        Pageable pageable
    ) {
        return ResponseEntity.ok(customerService.filterCustomers(filterRequest, pageable));
    }

    @GetMapping("/{customerId:\\d+}")
    public ResponseEntity<CustomerDetailResponse> getCustomerDetail(
        @PathVariable @Min(value = 1, message = "ID khách hàng phải lớn hơn 0.") Integer customerId
    ) {
        return ResponseEntity.ok(customerService.getCustomerDetail(customerId));
    }

    @PostMapping(path = {"", "/"})
    /**
     * Creates the customer.
     *
     * @param request the value supplied for this operation
     *
     * @return the created customer
     */
    public ResponseEntity<Integer> createCustomer(@Valid @RequestBody CreateCustomerRequest request) {
        Integer newId = customerService.createCustomer(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(newId);
    }

    @PutMapping("/{customerId:\\d+}")
    public ResponseEntity<Void> updateCustomer(
        @PathVariable @Min(value = 1, message = "ID khách hàng phải lớn hơn 0.") Integer customerId,
        @Valid @RequestBody UpdateCustomerRequest request
    ) {
        customerService.updateCustomer(customerId, request);
        return ResponseEntity.ok().build();
    }

    @Hidden
    @Deprecated
    @PatchMapping("/{customerId:\\d+}/toggle-active")
    public ResponseEntity<Void> toggleActive(
        @PathVariable @Min(value = 1, message = "ID khách hàng phải lớn hơn 0.") Integer customerId
    ) {
        customerService.toggleActive(customerId);
        return ResponseEntity.ok().build();
    }

    @Hidden
    @Deprecated
    @DeleteMapping("/{customerId:\\d+}")
    public ResponseEntity<Void> deleteCustomer(
        @PathVariable @Min(value = 1, message = "ID khách hàng phải lớn hơn 0.") Integer customerId
    ) {
        customerService.deleteCustomer(customerId);
        return ResponseEntity.noContent().build();
    }
}
