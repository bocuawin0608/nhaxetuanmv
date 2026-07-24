/**
 * REST controller for trip staff operations including passenger check-in
 * and cargo management for assigned trips.
 */
package com.ralsei.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ralsei.dto.projection.tripstaff.AssignedTripProjection;
import com.ralsei.dto.request.tripstaff.QrCheckInRequest;
import com.ralsei.dto.response.tripstaff.CheckInResponse;
import com.ralsei.dto.response.tripstaff.TripStaffCargoResponse;
import com.ralsei.dto.response.tripstaff.TripStaffDashboardResponse;
import com.ralsei.service.tripstaff.TripStaffCargoService;
import com.ralsei.service.tripstaff.TripStaffPassengerService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Trips", description = "Trip staff operations")
@RestController
@RequestMapping("/api/v1/staff/trips")
@PreAuthorize("hasRole('TRIP_STAFF')")
@RequiredArgsConstructor
@Validated
/**
 * Handles HTTP requests for trip staff operations.
 */
public class TripStaffController {

    private final TripStaffPassengerService tripStaffPassengerService;
    private final TripStaffCargoService tripStaffCargoService;

    @GetMapping
    public ResponseEntity<List<AssignedTripProjection>> getAssignedTrips(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(tripStaffPassengerService.getAssignedTrips(authorizationHeader, date));
    }

    @GetMapping("/{tripId}/passengers/dashboard")
    public ResponseEntity<TripStaffDashboardResponse> getDashboard(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable @Min(value = 1, message = "ID chuyến phải lớn hơn 0.") Integer tripId) {
        return ResponseEntity.ok(tripStaffPassengerService.getDashboard(authorizationHeader, tripId));
    }

    @PostMapping("/{tripId}/passengers/check-in/qr")
    public ResponseEntity<CheckInResponse> checkInByQr(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable @Min(value = 1, message = "ID chuyến phải lớn hơn 0.") Integer tripId,
            @Valid @RequestBody QrCheckInRequest request) {
        return ResponseEntity.ok(tripStaffPassengerService.checkInByQr(authorizationHeader, tripId, request));
    }

    @PostMapping("/{tripId}/passengers/{ticketDetailId}/check-in")
    public ResponseEntity<CheckInResponse> checkInManual(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable @Min(value = 1, message = "ID chuyến phải lớn hơn 0.") Integer tripId,
            @PathVariable @Min(value = 1, message = "ID chi tiết vé phải lớn hơn 0.") Integer ticketDetailId) {
        return ResponseEntity.ok(tripStaffPassengerService.checkInManual(authorizationHeader, tripId, ticketDetailId));
    }

    @GetMapping("/{tripId}/cargo")
    public ResponseEntity<TripStaffCargoResponse> getCargoList(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable @Min(value = 1, message = "ID chuyến phải lớn hơn 0.") Integer tripId) {
        return ResponseEntity.ok(tripStaffCargoService.getCargoList(authorizationHeader, tripId));
    }

    @PostMapping("/{tripId}/cargo/{cargoTicketId}/load")
    public ResponseEntity<Void> loadCargo(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable @Min(value = 1, message = "ID chuyến phải lớn hơn 0.") Integer tripId,
            @PathVariable @Min(value = 1, message = "ID đơn hàng phải lớn hơn 0.") Integer cargoTicketId) {
        tripStaffCargoService.loadCargo(authorizationHeader, tripId, cargoTicketId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{tripId}/cargo/{cargoTicketId}/unload")
    public ResponseEntity<Void> unloadCargo(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable @Min(value = 1, message = "ID chuyến phải lớn hơn 0.") Integer tripId,
            @PathVariable @Min(value = 1, message = "ID đơn hàng phải lớn hơn 0.") Integer cargoTicketId) {
        tripStaffCargoService.unloadCargo(authorizationHeader, tripId, cargoTicketId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{tripId}/start")
    public ResponseEntity<Void> startTrip(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable @Min(value = 1, message = "ID chuyến phải lớn hơn 0.") Integer tripId) {
        tripStaffPassengerService.startTrip(authorizationHeader, tripId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{tripId}/end")
    public ResponseEntity<Void> endTrip(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable @Min(value = 1, message = "ID chuyến phải lớn hơn 0.") Integer tripId) {
        tripStaffPassengerService.endTrip(authorizationHeader, tripId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{tripId}/incident")
    public ResponseEntity<Void> reportUnrecoverableIncident(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable @Min(value = 1, message = "ID chuyến phải lớn hơn 0.") Integer tripId) {
        tripStaffPassengerService.reportUnrecoverableIncident(authorizationHeader, tripId);
        return ResponseEntity.ok().build();
    }

    @Hidden
    @Deprecated
    @PostMapping("/{tripId}/passengers/{ticketDetailId}/no-show")
    public ResponseEntity<Void> markNoShow(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable @Min(value = 1, message = "ID chuyến phải lớn hơn 0.") Integer tripId,
            @PathVariable @Min(value = 1, message = "ID chi tiết vé phải lớn hơn 0.") Integer ticketDetailId) {
        tripStaffPassengerService.markNoShow(authorizationHeader, tripId, ticketDetailId);
        return ResponseEntity.ok().build();
    }
}
