package com.ralsei.controller;

import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.ralsei.dto.response.customer.CargoOrderLookupResponse;
import com.ralsei.service.CargoOrderLookupService;
import com.ralsei.service.JwtService;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** Authenticated read-only endpoint for customer-owned cargo order history. */
@Tag(name = "Cargo", description = "Customer cargo order history")
@RestController
@RequestMapping("/api/v1/customer/cargo-history")
@PreAuthorize("hasRole('CUSTOMER')")
@RequiredArgsConstructor
/**
 * Handles HTTP requests for cargo order lookup operations.
 */
public class CargoOrderLookupController {
    private final CargoOrderLookupService cargoOrderLookupService;
    private final JwtService jwtService;

    /** Uses only the token account claim and never trusts a client ownership value. */
    @GetMapping
    public ResponseEntity<List<CargoOrderLookupResponse>> getHistory(
        @RequestHeader("Authorization") String authorizationHeader
    ) {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(cargoOrderLookupService.findByAccountId(
                jwtService.extractAccountId(authorizationHeader)));
    }
}
