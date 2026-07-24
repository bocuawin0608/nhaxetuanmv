package com.ralsei.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ralsei.dto.request.staffrefund.StaffRefundCompleteRequest;
import com.ralsei.dto.response.PagedResponse;
import com.ralsei.dto.response.staffrefund.StaffRefundDetailResponse;
import com.ralsei.dto.response.staffrefund.StaffRefundListItemResponse;
import com.ralsei.service.JwtService;
import com.ralsei.service.staffrefund.StaffRefundService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Manager APIs for reviewing and completing passenger refund requests.
 */
@Tag(name = "Passenger Tickets", description = "Passenger refund review")
@RestController
@RequestMapping("/api/v1/refunds")
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
@RequiredArgsConstructor
@Validated
/**
 * Handles HTTP requests for staff refund operations.
 */
public class StaffRefundController {

    private final StaffRefundService staffRefundService;
    private final JwtService jwtService;

    /**
     * Lists passenger refund requests sorted by newest creation time first.
     */
    @GetMapping("/passenger")
    public ResponseEntity<PagedResponse<StaffRefundListItemResponse>> searchPassengerRefunds(
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String ticketCode,
        @RequestParam(required = false) String phone,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ResponseEntity.ok(staffRefundService.searchPassengerRefunds(
            status, ticketCode, phone, createdFrom, createdTo, page, size
        ));
    }

    /**
     * Loads one passenger refund request for manager review.
     */
    @GetMapping("/passenger/{refundId}")
    public ResponseEntity<StaffRefundDetailResponse> getPassengerRefundDetail(
        @PathVariable @Min(1) int refundId
    ) {
        return ResponseEntity.ok(staffRefundService.getPassengerRefundDetail(refundId));
    }

    /**
     * Confirms that a pending passenger refund was paid out to the customer.
     */
    @PatchMapping("/passenger/{refundId}/complete")
    public ResponseEntity<StaffRefundDetailResponse> completePassengerRefund(
        @RequestHeader("Authorization") String authorizationHeader,
        @PathVariable @Min(1) int refundId,
        @Valid @RequestBody StaffRefundCompleteRequest request
    ) {
        return ResponseEntity.ok(staffRefundService.completePassengerRefund(
            jwtService.extractAccountId(authorizationHeader),
            refundId,
            request
        ));
    }
}
