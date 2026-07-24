package com.ralsei.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ralsei.dto.projection.trip.TripDetailProjection;
import com.ralsei.dto.projection.trip.TripFilterProjection;
import com.ralsei.dto.projection.trip.StaffTripInfoProjection;
import com.ralsei.dto.projection.trip.TripSummaryProjection;
import com.ralsei.dto.projection.trip.TripStopProjection;
import com.ralsei.dto.request.trip.TripCreateRequest;
import com.ralsei.dto.request.trip.TripFilterRequest;
import com.ralsei.dto.request.trip.TripIncidentReplacementRequest;
import com.ralsei.dto.request.trip.TripSearchRequest;
import com.ralsei.dto.request.trip.TripUpdateRequest;
import com.ralsei.dto.response.PagedResponse;
import com.ralsei.dto.response.trip.ManagerTripIncidentResponse;
import com.ralsei.service.TripService;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
/**
 * HTTP entry point for public customer trip discovery and authenticated staff
 * trip operations. Each endpoint delegates business rules to {@link TripService}
 * and keeps request binding concerns at the web boundary.
 */
@Tag(name = "Trips", description = "Trip search and manager trip CRUD")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
/**
 * Handles HTTP requests for trip operations.
 */
public class TripController {
    private final TripService tripService;

    @GetMapping(value = "/trips/home", params = "!advanced") // NIKO: Bổ sung thêm /trips vào đây
    public ResponseEntity<PagedResponse<TripDetailProjection>> searchTrips(
            @Valid @ModelAttribute TripSearchRequest request) {

        LocalDateTime start = request.getDate().atStartOfDay();
        LocalDateTime end = request.getDate().atTime(23, 59, 59, 999_000_000);

        PagedResponse<TripDetailProjection> response = tripService.getTripDetails(
                start,
                end,
                request.getRoute(),
                request.getPage(),
                request.getSize());
        return ResponseEntity.ok(response);
    }

    /**
     * Searches public trips using validated customer-facing filters.
     *
     * @param request date, route, pagination, time, layout, and price filters
     * @return a page containing only selectable trips with active available seats
     */
    @GetMapping(value = "/trips/home", params = "advanced=true")
    public ResponseEntity<PagedResponse<TripFilterProjection>> filterTrips(
            @Valid @ModelAttribute TripFilterRequest request) {

        LocalDateTime start = request.getDate().atStartOfDay();
        LocalDateTime end = request.getDate().atTime(23, 59, 59, 999_000_000);

        PagedResponse<TripFilterProjection> response = tripService.getFilteredTripDetails(
                start,
                end,
                request.getRoute(),
                request.getTimeSlots(),
                request.getLayouts(),
                request.getMinPrice(),
                request.getMaxPrice(),
                request.getPage(),
                request.getSize());

        return ResponseEntity.ok(response);
    }

    /**
     * Exposes the ordered pickup and drop-off timeline for one public trip.
     *
     * @param tripId concrete trip selected on the customer search page
     * @return route stops derived from that trip's assigned route
     */
    @GetMapping("/trips/{tripId}/stops")
    @PreAuthorize("permitAll()")
    /**
     * Returns the trip stops.
     *
     * @param tripId the value supplied for this operation
     *
     * @return the trip stops
     */
    public ResponseEntity<List<TripStopProjection>> getTripStops(@PathVariable Integer tripId) {
        return ResponseEntity.ok(tripService.getTripStops(tripId));
    }

    @PostMapping("/manager/trips/create") // NIKO: Đổi từ /admin/create thành /manager/trips/create
    /**
     * Executes the insert trip operation.
     *
     * @param tripRequest the value supplied for this operation
     *
     * @return the operation result
     */
    public ResponseEntity<Map<String, String>> insertTrip(@RequestBody TripCreateRequest tripRequest) {
        String result = tripService.insertTrip(tripRequest);

        if (!result.contains("thành công")) {
            return ResponseEntity.badRequest().body(Map.of("message", result));
        }
        return ResponseEntity.ok(Map.of("message", result));
    }

    @PutMapping("/manager/trips/update/{tripId}")
    public ResponseEntity<Map<String, String>> updateTrip(
            @PathVariable Integer tripId,
            @RequestBody TripUpdateRequest updateRequest) {

        String result = tripService.updateTrip(tripId, updateRequest);
        if (!result.contains("thành công")) {
            return ResponseEntity.badRequest().body(Map.of("message", result));
        }
        return ResponseEntity.ok(Map.of("message", result));
    }

    @DeleteMapping("/manager/trips/delete/{tripId}")
    /**
     * Deletes the trip.
     *
     * @param tripId the value supplied for this operation
     */
    public ResponseEntity<Map<String, String>> deleteTrip(@PathVariable Integer tripId) {
        String result = tripService.deleteTrip(tripId);

        if (!result.contains("thành công")) {
            return ResponseEntity.badRequest().body(Map.of("message", result));
        }
        return ResponseEntity.ok(Map.of("message", result));
    }

    @GetMapping("/manager/trips/summaries") // NIKO: Đây chính là endpoint cứu rỗi lỗi 404/NoResourceFound ban nãy!
    public ResponseEntity<PagedResponse<TripSummaryProjection>> getAllTripSummaries(
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Integer routeId,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        PagedResponse<TripSummaryProjection> response = tripService.getAllTripSummaries(date, routeId, period, status, page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/manager/trips/incidents")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<List<ManagerTripIncidentResponse>> getManagerTripIncidents(
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            LocalDate date) {
        return ResponseEntity.ok(tripService.getManagerTripIncidents(date));
    }

    @GetMapping("/manager/trips/{tripId}/replacement-coaches")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<?> getIncidentReplacementCoaches(
            @PathVariable Integer tripId,
            @RequestParam Integer routeId) {
        return ResponseEntity.ok(tripService.getIncidentReplacementCoaches(tripId, routeId));
    }

    @GetMapping("/manager/trips/{tripId}/replacement-drivers")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<?> getIncidentReplacementDrivers(@PathVariable Integer tripId) {
        return ResponseEntity.ok(tripService.getIncidentReplacementDrivers(tripId));
    }

    @PutMapping("/manager/trips/{tripId}/replace-incident-coach")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<Map<String, String>> replaceIncidentCoach(
            @PathVariable Integer tripId,
            @Valid @RequestBody TripIncidentReplacementRequest request) {
        return ResponseEntity.ok(Map.of("message", tripService.replaceIncidentTrip(tripId, request)));
    }

    /**
     * Returns upcoming trips for ticket staff's "view trip info" flow.
     *
     * <p>This endpoint intentionally exposes operational fields that belong only
     * inside the staff portal: coach plate, driver, attendant, trip status, fare,
     * and seat counts. Filters are optional and map to the visible controls on
     * the trip-info screen.</p>
     */
    @GetMapping("/staff/trips/info")
    public ResponseEntity<PagedResponse<StaffTripInfoProjection>> getStaffTripInfos(
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String timeFrom,
            @RequestParam(required = false) String timeTo,
            @RequestParam(required = false) String coachTypeKeyword,
            @RequestParam(required = false) List<String> priceRanges,
            @RequestParam(required = false) List<String> statuses,
            @RequestParam(required = false) String driverName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PagedResponse<StaffTripInfoProjection> response = tripService.getStaffTripInfos(
                date, city, timeFrom, timeTo, coachTypeKeyword, priceRanges, statuses, driverName, page, size);
        return ResponseEntity.ok(response);
    }
    /** Returns coaches free for the requested route and departure window. */
    @GetMapping("/manager/trips/available-coaches")
    public ResponseEntity<?> getAvailableCoaches(
            @RequestParam Integer routeId,
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) LocalDateTime departureTime,
            @RequestParam(required = false) Integer excludeTripId) {
        return ResponseEntity.ok(tripService.getAvailableCoaches(routeId, departureTime, excludeTripId));
    }

    /** Returns drivers free for the requested departure window. */
    @GetMapping("/manager/trips/available-drivers")
    public ResponseEntity<?> getAvailableDrivers(
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) LocalDateTime departureTime,
            @RequestParam(required = false) Integer excludeTripId) {
        return ResponseEntity.ok(tripService.getAvailableDrivers(departureTime, excludeTripId));
    }

    /** Returns attendants free for the requested departure window. */
    @GetMapping("/manager/trips/available-attendants")
    public ResponseEntity<?> getAvailableAttendants(
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) LocalDateTime departureTime,
            @RequestParam(required = false) Integer excludeTripId) {
        return ResponseEntity.ok(tripService.getAvailableAttendants(departureTime, excludeTripId));
    }
}
