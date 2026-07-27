package com.ralsei.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ralsei.dto.projection.route.RouteNameDropdownProjection;
import com.ralsei.dto.projection.route.RouteLocationDropdownProjection;
import com.ralsei.dto.response.CoachAndRouteStop.RouteDropdownDTO;
import com.ralsei.model.Route;

/**
 * Provides persistence access for route data.
 */
public interface RouteRepository extends JpaRepository<Route, Integer> {

  @Query("""
      SELECT r FROM Route r
      WHERE (:isActive IS NULL OR r.isActive = :isActive)
        AND (:search IS NULL OR r.routeName LIKE %:search%)
      """)
  Page<Route> searchRoutes(
      @Param("search") String search,
      @Param("isActive") Boolean isActive,
      Pageable pageable);

  Optional<Route> findByRouteIdAndIsActiveTrue(Integer routeId);

  /**
   * Returns every active coach-stop location served by an active route in one
   * query. The customer homepage uses these fields instead of maintaining a
   * separate hard-coded address list.
   */
  @Query("SELECT new com.ralsei.dto.response.CoachAndRouteStop.RouteDropdownDTO(r.routeId, r.routeName) FROM Route r WHERE r.isActive = true ORDER BY r.routeName")
  List<RouteDropdownDTO> findRoutesForDropdown();

  /**
   * Returns active route/location rows for the public customer search form.
   * A dedicated projection protects the established RouteDropdownDTO and
   * findRoutesForDropdown contract used by other screens.
   */
  @Query("""
      SELECT DISTINCT r.routeId AS routeId,
                      cs.stopPointId AS stopPointId,
                      r.routeName AS routeName,
                      cs.city AS locationName,
                      cs.stopPointName AS stopPointName,
                      cs.address AS address
      FROM Route r
      JOIN r.routeStops rs
      JOIN rs.coachStop cs
      WHERE r.isActive = true
        AND cs.isActive = true
      ORDER BY cs.city, cs.stopPointId, r.routeName
      """)
  List<RouteLocationDropdownProjection> findRouteLocationsForCustomerDropdown();

  @Query(value = """
      SELECT routeName FROM ROUTE
          """, nativeQuery = true)
  List<RouteNameDropdownProjection> routeNameDropdown();

  @Modifying
  @Query("UPDATE Route r SET r.totalKilometers = :km, r.totalMinutes = :mins WHERE r.routeId = :routeId")
  void updateRouteTotals(@Param("routeId") int routeId, @Param("km") java.math.BigDecimal km, @Param("mins") int mins);
}
