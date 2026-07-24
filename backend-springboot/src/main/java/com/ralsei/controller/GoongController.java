package com.ralsei.controller;

import com.ralsei.dto.request.goong.DistanceTimeRequest;
import com.ralsei.dto.request.goong.CalculateRouteDistancesRequest;
import com.ralsei.dto.response.goong.DistanceTimeResponse;
import com.ralsei.dto.response.goong.CalculateRouteDistancesResponse;
import com.ralsei.dto.response.goong.GeocodeResponse;
import com.ralsei.service.GoongService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Goong", description = "Goong map proxy")
@RestController
@RequestMapping("/api/v2/goong")
@RequiredArgsConstructor
/**
 * Handles HTTP requests for goong operations.
 */
public class GoongController {

    private final GoongService goongService;

    @GetMapping("/place/autocomplete")
    /**
     * Executes the autocomplete operation.
     *
     * @param input the value supplied for this operation
     *
     * @return the operation result
     */
    public ResponseEntity<Object> autocomplete(@RequestParam String input) {
        return ResponseEntity.ok(goongService.autocomplete(input));
    }

    @Hidden
    @Deprecated
    @GetMapping("/geocode")
    /**
     * Executes the geocode operation.
     *
     * @param address the value supplied for this operation
     *
     * @return the operation result
     */
    public ResponseEntity<GeocodeResponse> geocode(@RequestParam String address) {
        return ResponseEntity.ok(goongService.geocode(address));
    }

    @Hidden
    @Deprecated
    @GetMapping("/place/distance-time")
    /**
     * Returns the distance and time.
     *
     * @param request the value supplied for this operation
     *
     * @return the distance and time
     */
    public ResponseEntity<DistanceTimeResponse> getDistanceAndTime(@Valid @ModelAttribute DistanceTimeRequest request) {
        return ResponseEntity.ok(goongService.getDistanceAndTime(request));
    }

    @PostMapping("/calculate-route-distances")
    public ResponseEntity<CalculateRouteDistancesResponse> calculateRouteDistances(
            @Valid @RequestBody CalculateRouteDistancesRequest request) {
        return ResponseEntity.ok(goongService.calculateRouteDistances(request));
    }
}
