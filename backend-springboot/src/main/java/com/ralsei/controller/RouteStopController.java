package com.ralsei.controller;

import com.ralsei.dto.request.CoachAndRouteStop.RouteStopRequest;
import com.ralsei.dto.response.PagedResponse;
import com.ralsei.dto.response.CoachAndRouteStop.RouteStopResponse;
import com.ralsei.dto.request.route.RouteStopOrderUpdateRequest;
import com.ralsei.service.RouteStopService;
import java.util.List;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Routes", description = "Route stop management")
@RestController
@RequestMapping("/api/v1/route-stops")
@RequiredArgsConstructor
/**
 * Handles HTTP requests for route stop operations.
 */
public class RouteStopController {

    private final RouteStopService routeStopService;

    @PostMapping
    /**
     * Creates the route stop.
     *
     * @param request the value supplied for this operation
     *
     * @return the created route stop
     */
    public ResponseEntity<RouteStopResponse> createRouteStop(@Valid @RequestBody RouteStopRequest request) {
        RouteStopResponse response = routeStopService.createRouteStop(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Hidden
    @Deprecated
    @PutMapping("/{id}")
    public ResponseEntity<RouteStopResponse> updateRouteStop(
            @PathVariable int id,
            @Valid @RequestBody RouteStopRequest request) {
        RouteStopResponse response = routeStopService.updateRouteStop(id, request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/bulk-update-orders")
    public ResponseEntity<List<RouteStopResponse>> bulkUpdateOrders(
            @Valid @RequestBody List<RouteStopOrderUpdateRequest> requests) {
        List<RouteStopResponse> responses = routeStopService.bulkUpdateOrders(requests);
        return ResponseEntity.ok(responses);
    }

    @Hidden
    @Deprecated
    @GetMapping("/{id}")
    /**
     * Returns the route stop by id.
     *
     * @param id the value supplied for this operation
     *
     * @return the route stop by id
     */
    public ResponseEntity<RouteStopResponse> getRouteStopById(@PathVariable int id) {
        RouteStopResponse response = routeStopService.getRouteStopById(id);
        return ResponseEntity.ok(response);
    }

    @Hidden
    @Deprecated
    @GetMapping
    public ResponseEntity<PagedResponse<RouteStopResponse>> getAllRouteStops(
            @RequestParam(required = false, defaultValue = "0") int routeId,
            @RequestParam(required = false, defaultValue = "0") int stopPointId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PagedResponse<RouteStopResponse> response = routeStopService.getAllRouteStops(routeId, stopPointId,
                page, size);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    /**
     * Deletes the route stop.
     *
     * @param id the value supplied for this operation
     */
    public ResponseEntity<Void> deleteRouteStop(@PathVariable int id) {
        routeStopService.deleteRouteStop(id);
        return ResponseEntity.noContent().build();
    }
}
