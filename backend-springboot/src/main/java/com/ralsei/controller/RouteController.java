package com.ralsei.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ralsei.dto.request.CoachAndRouteStop.RouteRequest;
import com.ralsei.dto.request.CoachAndRouteStop.RouteWithStopsRequest;
import com.ralsei.dto.response.CoachAndRouteStop.RouteDropdownDTO;
import com.ralsei.dto.response.CoachAndRouteStop.RouteResponse;
import com.ralsei.dto.response.CoachAndRouteStop.RouteWithStopsResponse;
import com.ralsei.dto.response.PagedResponse;
import com.ralsei.service.RouteService;
import com.ralsei.dto.projection.route.RouteLocationDropdownProjection;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/routes")
@RequiredArgsConstructor
/**
 * Handles HTTP requests for route operations.
 */
public class RouteController {

    private final RouteService routeService;

    @PostMapping
    /**
     * Creates the route.
     *
     * @param request the value supplied for this operation
     *
     * @return the created route
     */
    public ResponseEntity<RouteResponse> createRoute(@Valid @RequestBody RouteRequest request) {
        RouteResponse response = routeService.createRoute(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/with-stops")
    /**
     * Creates the route with stops.
     *
     * @param request the value supplied for this operation
     *
     * @return the created route with stops
     */
    public ResponseEntity<RouteWithStopsResponse> createRouteWithStops(
            @Valid @RequestBody RouteWithStopsRequest request) {
        RouteWithStopsResponse response = routeService.createRouteWithStops(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<RouteResponse> updateRoute(
            @PathVariable int id,
            @Valid @RequestBody RouteRequest request) {
        RouteResponse response = routeService.updateRoute(id, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/dropdown")
    /**
     * Searches for routes for dropdown records.
     *
     * @return the matching results
     */
    public ResponseEntity<List<RouteDropdownDTO>> searchRoutesForDropdown() {
        return ResponseEntity.ok(routeService.findRoutesForDropdown());
    }

    /**
     * Supplies real coach-stop cities to the public trip search without altering
     * the existing route dropdown endpoint used elsewhere in the application.
     */
    @GetMapping("/customer-locations")
    public ResponseEntity<List<RouteLocationDropdownProjection>> getCustomerRouteLocations() {
        return ResponseEntity.ok(routeService.findRouteLocationsForCustomerDropdown());
    }

    @GetMapping("/{id:\\d+}")
    /**
     * Returns the route by id.
     *
     * @param id the value supplied for this operation
     *
     * @return the route by id
     */
    public ResponseEntity<RouteResponse> getRouteById(@PathVariable int id) {
        RouteResponse response = routeService.getRouteById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<PagedResponse<RouteResponse>> getAllRoutes(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PagedResponse<RouteResponse> response = routeService.getAllRoutes(search, isActive, page, size);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/soft-delete")
    /**
     * Executes the soft delete route operation.
     *
     * @param id the value supplied for this operation
     *
     * @return the operation result
     */
    public ResponseEntity<Void> softDeleteRoute(@PathVariable int id) {
        routeService.softDeleteRoute(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/restore")
    /**
     * Executes the restore route operation.
     *
     * @param id the value supplied for this operation
     *
     * @return the operation result
     */
    public ResponseEntity<Void> restoreRoute(@PathVariable int id) {
        routeService.restoreRoute(id);
        return ResponseEntity.noContent().build();
    }
}
