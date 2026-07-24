package com.ralsei.controller;

import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.ralsei.dto.response.customer.CustomerTicketHistoryResponse;
import com.ralsei.dto.request.customer.CustomerTicketCancellationRequest;
import com.ralsei.dto.response.customer.CustomerTicketCancellationResponse;
import com.ralsei.service.CustomerTicketHistoryService;
import com.ralsei.service.JwtService;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Exposes booking history and boarding QR images only to authenticated customers.
 */
@Tag(name = "Customer", description = "Customer ticket history and QR")
@RestController
@RequestMapping("/api/v1/customer/history")
@PreAuthorize("hasRole('CUSTOMER')")
@RequiredArgsConstructor
@Validated
/**
 * Handles HTTP requests for customer ticket history operations.
 */
public class CustomerTicketHistoryController {

    private final CustomerTicketHistoryService historyService;
    private final JwtService jwtService;

    /**
     * Returns the complete history for the current security principal.
     */
    @GetMapping
    public ResponseEntity<List<CustomerTicketHistoryResponse>> getHistory(
        @RequestHeader("Authorization") String authorizationHeader
    ) {
        return ResponseEntity.ok(historyService.getHistory(jwtService.extractAccountId(authorizationHeader)));
    }

    /**
     * Returns an owned ticket selected from the history list or notification panel.
     */
    @GetMapping("/{ticketCode:[A-Za-z0-9_-]+}")
    public ResponseEntity<CustomerTicketHistoryResponse> getDetail(
        @RequestHeader("Authorization") String authorizationHeader,
        @PathVariable @Pattern(regexp = "[A-Za-z0-9_-]{3,64}") String ticketCode
    ) {
        return ResponseEntity.ok(historyService.getDetail(
            jwtService.extractAccountId(authorizationHeader), ticketCode));
    }

    /**
     * Produces a non-cacheable QR image after verifying seat ownership.
     */
    @GetMapping(value = "/seats/{ticketDetailId}/qr", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> getSeatQr(
        @RequestHeader("Authorization") String authorizationHeader,
        @PathVariable @Min(1) Integer ticketDetailId
    ) {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .contentType(MediaType.IMAGE_PNG)
            .body(historyService.getSeatQrImage(
                jwtService.extractAccountId(authorizationHeader), ticketDetailId));
    }

    /**
     * Cancels an owned future ticket and records the customer's bank refund request.
     */
    @PostMapping("/{ticketCode:[A-Za-z0-9_-]+}/cancel")
    public ResponseEntity<CustomerTicketCancellationResponse> cancelTicket(
        @RequestHeader("Authorization") String authorizationHeader,
        @PathVariable @Pattern(regexp = "[A-Za-z0-9_-]{3,64}") String ticketCode,
        @Valid @RequestBody CustomerTicketCancellationRequest request
    ) {
        return ResponseEntity.ok(historyService.cancelTicket(
            jwtService.extractAccountId(authorizationHeader), ticketCode, request));
    }
}
