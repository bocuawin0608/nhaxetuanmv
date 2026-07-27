package com.ralsei.dto.projection.route;

/**
 * Customer-search view of one active city served by an active route.
 * This projection is intentionally separate from {@code RouteDropdownDTO},
 * whose established two-field contract is shared by existing consumers.
 */
/**
 * Projects the route location dropdow data shape for query results.
 */
public interface RouteLocationDropdownProjection {

    /** Identifies the route serving this location. */
    Integer getRouteId();

    /** Identifies the coach stop so clients can remove reverse-route duplicates. */
    Integer getStopPointId();

    /** Returns the existing route display name without changing its DTO contract. */
    String getRouteName();

    /** Returns the normalized source city stored on the active coach stop. */
    String getLocationName();

    /** Returns the customer-facing name of the active coach stop. */
    String getStopPointName();

    /** Returns the real street address stored on the active coach stop. */
    String getAddress();
}
