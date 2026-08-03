package com.ralsei.service.impl;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Comparator;
import java.util.ArrayList;

import com.ralsei.service.GoongService;

import com.ralsei.dto.request.CoachAndRouteStop.RouteRequest;
import com.ralsei.dto.request.CoachAndRouteStop.RouteStopCreateRequest;
import com.ralsei.dto.request.CoachAndRouteStop.RouteWithStopsRequest;
import com.ralsei.dto.projection.route.RouteLocationDropdownProjection;
import com.ralsei.dto.response.CoachAndRouteStop.RouteDropdownDTO;
import com.ralsei.dto.response.CoachAndRouteStop.RouteResponse;
import com.ralsei.dto.response.CoachAndRouteStop.RouteStopResponse;
import com.ralsei.dto.response.CoachAndRouteStop.RouteWithStopsResponse;
import com.ralsei.dto.response.PagedResponse;
import com.ralsei.exception.ResourceNotFoundException;
import com.ralsei.model.CoachStop;
import com.ralsei.model.Route;
import com.ralsei.model.RouteStop;
import com.ralsei.repository.CoachStopRepository;
import com.ralsei.repository.RouteRepository;
import com.ralsei.service.RouteService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
/**
 * Provides the route service impl component for the application.
 */
public class RouteServiceImpl implements RouteService {

    private final RouteRepository routeRepository;
    private final CoachStopRepository coachStopRepository;
    private final GoongService goongService;

    @Override
    @Transactional
    /**
     * Creates the route.
     *
     * @param request the value supplied for this operation
     *
     * @return the created route
     */
    public RouteResponse createRoute(RouteRequest request) {
        Route route = Route.builder()
                .routeName(request.getRouteName())
                .totalKilometers(request.getTotalKilometers())
                .totalMinutes(request.getTotalMinutes())
                .isActive(true)
                .routeStops(new HashSet<>())
                .build();
        Route saved = routeRepository.save(Objects.requireNonNull(route));
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    /**
     * Creates the route with stops.
     *
     * @param request the value supplied for this operation
     *
     * @return the created route with stops
     */
    public RouteWithStopsResponse createRouteWithStops(RouteWithStopsRequest request) {
        Route route = Route.builder()
                .routeName(request.getRouteName())
                .isActive(true)
                .routeStops(new HashSet<>())
                .build();

        List<RouteStopCreateRequest> stopRequests = request.getRouteStops();
        if (stopRequests == null || stopRequests.size() < 2) {
            throw new IllegalArgumentException("A route must have at least 2 stops.");
        }

        List<RouteStop> sortedStops = new ArrayList<>();

        for (RouteStopCreateRequest stopRequest : stopRequests) {
            CoachStop coachStop = coachStopRepository.findById(Objects.requireNonNull(stopRequest.getStopPointId()))
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "CoachStop not found with ID: " + stopRequest.getStopPointId()));

            boolean orderExists = sortedStops.stream()
                    .anyMatch(rs -> rs.getStopOrder() == stopRequest.getStopOrder());
            if (orderExists) {
                throw new IllegalArgumentException(
                        "Stop order " + stopRequest.getStopOrder() + " already exists in this route.");
            }

            RouteStop routeStop = RouteStop.builder()
                    .route(route)
                    .coachStop(coachStop)
                    .stopOrder(stopRequest.getStopOrder())
                    .build();

            sortedStops.add(routeStop);
        }

        // Sort by order
        sortedStops.sort(Comparator.comparingInt(RouteStop::getStopOrder));

        // Call Goong API to calculate distances
        goongService.calculateAndSetRouteStopsDistances(sortedStops);

        // Assign total distance and total time to Route
        route.setTotalKilometers(sortedStops.get(sortedStops.size() - 1).getKilometersFromStart());
        route.setTotalMinutes(sortedStops.get(sortedStops.size() - 1).getMinutesFromStart());

        route.getRouteStops().addAll(sortedStops);

        Route saved = routeRepository.save(Objects.requireNonNull(route));
        return mapToRouteWithStopsResponse(saved);
    }

    @Override
    @Transactional
    /**
     * Updates the route.
     *
     * @param id      the value supplied for this operation
     * @param request the value supplied for this operation
     *
     * @return the updated route
     */
    public RouteResponse updateRoute(int id, RouteRequest request) {
        Route route = routeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Route not found with ID: " + id));

        route.setRouteName(request.getRouteName());
        route.setTotalKilometers(request.getTotalKilometers());
        route.setTotalMinutes(request.getTotalMinutes());

        Route updated = routeRepository.save(route);
        return mapToResponse(updated);
    }

    @Override
    @Transactional(readOnly = true)
    /**
     * Returns the route by id.
     *
     * @param id the value supplied for this operation
     *
     * @return the route by id
     */
    public RouteResponse getRouteById(int id) {
        Route route = routeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Route not found with ID: " + id));
        return mapToResponse(route);
    }

    @Override
    @Transactional(readOnly = true)
    /**
     * Returns the all routes.
     *
     * @param search   the value supplied for this operation
     * @param isActive the value supplied for this operation
     * @param page     the value supplied for this operation
     * @param size     the value supplied for this operation
     *
     * @return the all routes
     */
    public PagedResponse<RouteResponse> getAllRoutes(String search, Boolean isActive, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("routeId").descending());
        String safeSearch = search != null ? search.trim() : null;
        Page<Route> routePage = routeRepository.searchRoutes(safeSearch, isActive, pageable);

        List<RouteResponse> content = routePage.getContent().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        return new PagedResponse<>(
                content,
                routePage.getNumber(),
                routePage.getSize(),
                routePage.getTotalElements(),
                routePage.getTotalPages(),
                routePage.isLast());
    }

    @Override
    @Transactional
    /**
     * Executes the soft delete route operation.
     *
     * @param id the value supplied for this operation
     */
    public void softDeleteRoute(int id) {
        Route route = routeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Route not found with ID: " + id));

        // Soft delete the route itself
        route.setActive(false);
        routeRepository.save(route);
    }

    @Override
    @Transactional
    /**
     * Executes the restore route operation.
     *
     * @param id the value supplied for this operation
     */
    public void restoreRoute(int id) {
        Route route = routeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Route not found with ID: " + id));

        if (route.getRouteStops() != null && route.getRouteStops().size() == 2) {
            boolean anyInactive = route.getRouteStops().stream()
                    .anyMatch(rs -> rs.getCoachStop() != null && !rs.getCoachStop().isActive());
            if (anyInactive) {
                throw new IllegalArgumentException(
                        "Không thể kích hoạt tuyến đường này vì có điểm dừng chưa được kích hoạt.");
            }
        }

        // Restore the route itself
        route.setActive(true);
        routeRepository.save(route);
    }

    private RouteResponse mapToResponse(Route route) {
        if (route == null) {
            return null;
        }
        List<RouteStopResponse> stopResponses = null;
        if (route.getRouteStops() != null) {
            stopResponses = route.getRouteStops().stream()
                    .map(this::mapStopToResponse)
                    .collect(Collectors.toList());
        }
        return RouteResponse.builder()
                .routeId(route.getRouteId())
                .routeName(route.getRouteName())
                .totalKilometers(route.getTotalKilometers())
                .totalMinutes(route.getTotalMinutes())
                .isActive(route.isActive())
                .createdAt(route.getCreatedAt())
                .createdBy(route.getCreatedBy())
                .updatedAt(route.getUpdatedAt())
                .updatedBy(route.getUpdatedBy())
                .routeStops(stopResponses)
                .build();
    }

    private RouteWithStopsResponse mapToRouteWithStopsResponse(Route route) {
        if (route == null) {
            return null;
        }
        List<RouteStopResponse> stopResponses = null;
        if (route.getRouteStops() != null) {
            stopResponses = route.getRouteStops().stream()
                    .map(this::mapStopToResponse)
                    .collect(Collectors.toList());
        }
        return RouteWithStopsResponse.builder()
                .routeId(route.getRouteId())
                .routeName(route.getRouteName())
                .totalKilometers(route.getTotalKilometers())
                .totalMinutes(route.getTotalMinutes())
                .isActive(route.isActive())
                .createdAt(route.getCreatedAt())
                .createdBy(route.getCreatedBy())
                .updatedAt(route.getUpdatedAt())
                .updatedBy(route.getUpdatedBy())
                .routeStops(stopResponses)
                .build();
    }

    private RouteStopResponse mapStopToResponse(RouteStop rs) {
        if (rs == null) {
            return null;
        }
        return RouteStopResponse.builder()
                .routeStopId(rs.getRouteStopId())
                .routeId(rs.getRoute() != null ? rs.getRoute().getRouteId() : 0)
                .routeName(rs.getRoute() != null ? rs.getRoute().getRouteName() : null)
                .stopPointId(rs.getCoachStop() != null ? rs.getCoachStop().getStopPointId() : 0)
                .stopPointName(rs.getCoachStop() != null ? rs.getCoachStop().getStopPointName() : null)
                .address(rs.getCoachStop() != null ? rs.getCoachStop().getAddress() : null)
                .city(rs.getCoachStop() != null ? rs.getCoachStop().getCity() : null)
                .stopOrder(rs.getStopOrder())
                .kilometersFromStart(rs.getKilometersFromStart())
                .minutesFromStart(rs.getMinutesFromStart())
                .build();
    }

    @Override
    /**
     * Finds the routes for dropdown.
     *
     * @return the matching result
     */
    public List<RouteDropdownDTO> findRoutesForDropdown() {
        return routeRepository.findRoutesForDropdown();
    }

    /** {@inheritDoc} */
    @Override
    /**
     * Finds the route locations for customer dropdown.
     *
     * @return the matching result
     */
    public List<RouteLocationDropdownProjection> findRouteLocationsForCustomerDropdown() {
        return routeRepository.findRouteLocationsForCustomerDropdown();
    }
}
