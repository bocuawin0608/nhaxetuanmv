package com.ralsei.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ralsei.dto.request.passengerbooking.SeatLockRequest;
import com.ralsei.dto.request.staffpassengerticket.StaffPassengerChangePassengerRequest;
import com.ralsei.dto.request.staffpassengerticket.StaffPassengerChangeSeatRequest;
import com.ralsei.dto.request.staffpassengerticket.StaffPassengerItineraryChangeRequest;
import com.ralsei.dto.request.staffpassengerticket.StaffPassengerTicketCancelRequest;
import com.ralsei.dto.request.staffpassengerticket.StaffPassengerTicketChangesRequest;
import com.ralsei.dto.response.PagedResponse;
import com.ralsei.dto.response.passengerbooking.SeatLockResponse;
import com.ralsei.dto.response.passengerbooking.TripSeatResponse;
import com.ralsei.dto.response.staffpassengerticket.StaffPassengerItineraryPreviewResponse;
import com.ralsei.dto.response.staffpassengerticket.StaffPassengerTicketDetailResponse;
import com.ralsei.dto.response.staffpassengerticket.StaffPassengerTicketListItemResponse;
import com.ralsei.dto.response.staffpassengerticket.StaffPassengerTransferCandidateResponse;
import com.ralsei.service.JwtService;
import com.ralsei.service.passengerticket.StaffPassengerTicketCancelService;
import com.ralsei.service.passengerticket.StaffPassengerTicketChangeService;
import com.ralsei.service.passengerticket.StaffPassengerTicketQueryService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Passenger Tickets", description = "Staff passenger ticket operations")
@RestController
@RequestMapping("/api/v1/staff/passenger-tickets")
@PreAuthorize("hasRole('TICKET_STAFF')")
@RequiredArgsConstructor
@Validated
/**
 * Handles HTTP requests for staff passenger ticket operations.
 */
public class StaffPassengerTicketController {

    private final StaffPassengerTicketQueryService queryService;
    private final StaffPassengerTicketChangeService changeService;
    private final StaffPassengerTicketCancelService cancelService;
    private final JwtService jwtService;

    @GetMapping("/search")
    public ResponseEntity<PagedResponse<StaffPassengerTicketListItemResponse>> search(
        @RequestParam(required = false) String phone,
        @RequestParam(required = false) String ticketCode,
        @RequestParam(required = false) List<String> statuses,
        @RequestParam(required = false) Integer routeId,
        @RequestParam(required = false) Integer tripId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate departureDate,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ResponseEntity.ok(queryService.search(
            phone, ticketCode, statuses, routeId, tripId, departureDate, page, size
        ));
    }

    @GetMapping("/{ticketCode:[A-Za-z0-9_-]+}")
    public ResponseEntity<StaffPassengerTicketDetailResponse> getDetail(
        @PathVariable @Pattern(regexp = "[A-Za-z0-9_-]{3,64}") String ticketCode
    ) {
        return ResponseEntity.ok(queryService.getDetail(ticketCode));
    }

    @Hidden
    @Deprecated
    @PatchMapping("/{ticketCode:[A-Za-z0-9_-]+}/details/{detailId}/passenger-info")
    public ResponseEntity<StaffPassengerTicketDetailResponse> changePassengerInfo(
        @RequestHeader("Authorization") String authorizationHeader,
        @PathVariable @Pattern(regexp = "[A-Za-z0-9_-]{3,64}") String ticketCode,
        @PathVariable("detailId") @Min(1) Integer ticketDetailId,
        @Valid @RequestBody StaffPassengerChangePassengerRequest request
    ) {
        return ResponseEntity.ok(changeService.changePassengerInfo(
            jwtService.extractAccountId(authorizationHeader),
            ticketCode,
            ticketDetailId,
            request
        ));
    }

    @PostMapping("/{ticketCode:[A-Za-z0-9_-]+}/cancel")
    public ResponseEntity<StaffPassengerTicketDetailResponse> cancelFull(
        @RequestHeader("Authorization") String authorizationHeader,
        @PathVariable @Pattern(regexp = "[A-Za-z0-9_-]{3,64}") String ticketCode,
        @Valid @RequestBody StaffPassengerTicketCancelRequest request
    ) {
        return ResponseEntity.ok(cancelService.cancelFull(
            jwtService.extractAccountId(authorizationHeader),
            ticketCode,
            request
        ));
    }

    @GetMapping("/trips/{tripId}/seat-map")
    public ResponseEntity<List<TripSeatResponse>> getSeatMap(
        @PathVariable @Min(1) Integer tripId
    ) {
        return ResponseEntity.ok(changeService.getSeatMap(tripId));
    }

    @PostMapping("/trips/{tripId}/seats/lock")
    public ResponseEntity<SeatLockResponse> lockSeats(
        @PathVariable @Min(1) Integer tripId,
        @Valid @RequestBody SeatLockRequest request,
        @RequestHeader("X-Staff-Seat-Session") @NotBlank String holdToken,
        @RequestHeader(value = "X-Staff-Seat-Lock-Mode", defaultValue = "CHANGE_SEAT") String lockMode
    ) {
        return ResponseEntity.ok(changeService.lockSeats(tripId, request, holdToken, lockMode));
    }

    @PostMapping("/trips/{tripId}/seats/release")
    public ResponseEntity<Void> releaseSeats(
        @PathVariable @Min(1) Integer tripId,
        @Valid @RequestBody SeatLockRequest request,
        @RequestHeader("X-Staff-Seat-Session") @NotBlank String holdToken
    ) {
        changeService.releaseSeats(
            request.tripSeatIds(),
            holdToken,
            request.restoreVacatedTripSeatIds()
        );
        return ResponseEntity.noContent().build();
    }

    @Hidden
    @Deprecated
    @PatchMapping("/{ticketCode:[A-Za-z0-9_-]+}/details/{detailId}/seat")
    public ResponseEntity<StaffPassengerTicketDetailResponse> changeSeat(
        @RequestHeader("Authorization") String authorizationHeader,
        @RequestHeader("X-Staff-Seat-Session") @NotBlank String holdToken,
        @PathVariable @Pattern(regexp = "[A-Za-z0-9_-]{3,64}") String ticketCode,
        @PathVariable("detailId") @Min(1) Integer ticketDetailId,
        @Valid @RequestBody StaffPassengerChangeSeatRequest request
    ) {
        return ResponseEntity.ok(changeService.changeSeat(
            jwtService.extractAccountId(authorizationHeader),
            ticketCode,
            ticketDetailId,
            request,
            holdToken
        ));
    }

    @GetMapping("/{ticketCode:[A-Za-z0-9_-]+}/transfer-candidates")
    public ResponseEntity<List<StaffPassengerTransferCandidateResponse>> getTransferCandidates(
        @PathVariable @Pattern(regexp = "[A-Za-z0-9_-]{3,64}") String ticketCode,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate departureDate,
        @RequestParam @Min(1) Integer routeId,
        @RequestParam(defaultValue = "true") boolean excludeCurrentTrip
    ) {
        return ResponseEntity.ok(changeService.getTransferCandidates(
            ticketCode, departureDate, routeId, excludeCurrentTrip
        ));
    }

    @GetMapping("/{ticketCode:[A-Za-z0-9_-]+}/itinerary-preview")
    public ResponseEntity<StaffPassengerItineraryPreviewResponse> previewItineraryChange(
        @PathVariable @Pattern(regexp = "[A-Za-z0-9_-]{3,64}") String ticketCode,
        @RequestParam(required = false) Integer newTripId,
        @RequestParam @Min(1) Integer pickupStopId,
        @RequestParam @Min(1) Integer dropoffStopId,
        @RequestParam(required = false) List<@Min(1) Integer> newTripSeatIds
    ) {
        return ResponseEntity.ok(changeService.previewItineraryChange(
            ticketCode, newTripId, pickupStopId, dropoffStopId, newTripSeatIds
        ));
    }

    @Hidden
    @Deprecated
    @PatchMapping("/{ticketCode:[A-Za-z0-9_-]+}/itinerary")
    public ResponseEntity<StaffPassengerTicketDetailResponse> changeItinerary(
        @RequestHeader("Authorization") String authorizationHeader,
        @RequestHeader(value = "X-Staff-Seat-Session", required = false) String holdToken,
        @PathVariable @Pattern(regexp = "[A-Za-z0-9_-]{3,64}") String ticketCode,
        @Valid @RequestBody StaffPassengerItineraryChangeRequest request
    ) {
        return ResponseEntity.ok(changeService.changeItinerary(
            jwtService.extractAccountId(authorizationHeader),
            ticketCode,
            request,
            holdToken
        ));
    }

    @PostMapping("/{ticketCode:[A-Za-z0-9_-]+}/changes")
    public ResponseEntity<StaffPassengerTicketDetailResponse> confirmChanges(
        @RequestHeader("Authorization") String authorizationHeader,
        @RequestHeader(value = "X-Staff-Seat-Session", required = false) String holdToken,
        @PathVariable @Pattern(regexp = "[A-Za-z0-9_-]{3,64}") String ticketCode,
        @Valid @RequestBody StaffPassengerTicketChangesRequest request
    ) {
        return ResponseEntity.ok(changeService.confirmChanges(
            jwtService.extractAccountId(authorizationHeader),
            ticketCode,
            request,
            holdToken
        ));
    }
}
