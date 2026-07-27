package com.ralsei.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.query.Procedure;
import org.springframework.data.repository.query.Param;

import com.ralsei.dto.projection.cargoticket.CargoTicketTripOptionProjection;
import com.ralsei.dto.projection.cargoticket.CargoTicketTripOptionWithCoachTypeProjection;
import com.ralsei.dto.projection.cargoticket.CargoOperationalTripProjection;
import com.ralsei.dto.projection.cargoticket.CargoReceivingTripProjection;
import com.ralsei.dto.projection.coach.CoachUpcomingTripCountProjection;
import com.ralsei.dto.projection.staffpassengerticket.StaffPassengerTransferCandidateProjection;
import com.ralsei.dto.projection.trip.StaffTripInfoProjection;
import com.ralsei.dto.projection.trip.TripDetailProjection;
import com.ralsei.dto.projection.trip.TripFilterProjection;
import com.ralsei.dto.projection.trip.TripResourceProjection;
import com.ralsei.dto.projection.trip.TripStopProjection;
import com.ralsei.dto.projection.trip.TripSummaryProjection;
import com.ralsei.model.Trip;
import com.ralsei.model.enums.CoachStatus;

import jakarta.transaction.Transactional;

/**
 * Persistence boundary for trip workflows.
 *
 * <p>Customer and staff queries coexist here but have different visibility
 * rules. Customer queries expose only scheduled, future, priced, selectable
 * trips; staff queries retain operational records needed for management.</p>
 */
/**
 * Provides persistence access for trip data.
 */
public interface TripRepository extends JpaRepository<Trip, Integer> {

    @Query("""
            SELECT t FROM Trip t
            JOIN FETCH t.route
            JOIN FETCH t.coach c
            WHERE c.status = :coachStatus
              AND t.departureTime >= :start
              AND t.departureTime < :end
            ORDER BY t.departureTime, t.tripId
            """)
    List<Trip> findIncidentTrips(
            @Param("coachStatus") CoachStatus coachStatus,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    /**
     * Lists only coaches that have unloaded at least one order which still
     * requires acknowledgement by the supplied destination ticket agency.
     * Status is authoritative; nullable legacy audit fields do not hide rows.
     */
    @Query(value = """
            SELECT t.tripId AS tripId,
                   r.routeName AS routeName,
                   t.departureTime AS departureTime,
                   t.[status] AS tripStatus,
                   coach.licensePlate AS licensePlate,
                   coachType.coachTypeName AS coachTypeName,
                   driver.staffName AS driverName,
                   driver.phone AS driverPhone,
                   driver.cccd AS driverCccd,
                   attendant.staffName AS attendantName,
                   attendant.phone AS attendantPhone,
                   attendant.cccd AS attendantCccd,
                   MAX(cargo.updatedAt) AS lastCargoUpdateAt,
                   COUNT(cargo.cargoTicketId) AS waitingOrderCount
            FROM cargo_ticket cargo
            JOIN trip t ON t.tripId = cargo.tripId
            JOIN route r ON r.routeId = t.routeId
            JOIN coach coach ON coach.coachId = t.coachId
            JOIN coach_type coachType ON coachType.coachTypeId = coach.coachTypeId
            JOIN ticket_agency destination
              ON destination.stopPointId = cargo.dropoffStopId
             AND destination.ticketAgencyId = :ticketAgencyId
             AND destination.isActive = 1
            LEFT JOIN staff driver ON driver.staffId = t.driverId
            LEFT JOIN staff attendant ON attendant.staffId = t.attendantId
            WHERE cargo.[status] = 'ARRIVED'
            GROUP BY t.tripId, r.routeName, t.departureTime, t.[status],
                     coach.licensePlate, coachType.coachTypeName,
                     driver.staffName, driver.phone, driver.cccd,
                     attendant.staffName, attendant.phone, attendant.cccd
            ORDER BY MAX(cargo.updatedAt) DESC, t.tripId DESC
            """, countQuery = """
            SELECT COUNT(DISTINCT t.tripId)
            FROM cargo_ticket cargo
            JOIN trip t ON t.tripId = cargo.tripId
            JOIN ticket_agency destination
              ON destination.stopPointId = cargo.dropoffStopId
             AND destination.ticketAgencyId = :ticketAgencyId
             AND destination.isActive = 1
            WHERE cargo.[status] = 'ARRIVED'
            """, nativeQuery = true)
    Page<CargoReceivingTripProjection> findCargoReceivingTrips(
            @Param("ticketAgencyId") int ticketAgencyId,
            Pageable pageable);

    /**
     * Lists today's coaches which will call at the authenticated staff member's
     * exact agency stop and still have cargo capacity.
     */
    @Query(value = """
            SELECT t.tripId AS tripId, t.routeId AS routeId,
                   CONCAT(originStop.city, N' - ', destinationStop.city) AS routeName,
                   t.departureTime AS departureTime,
                   DATEADD(MINUTE, agencyRouteStop.minutesFromStart, t.departureTime) AS pickupTime,
                   agencyStop.stopPointId AS pickupStopId,
                   agencyStop.stopPointName AS pickupStopName,
                   agencyStop.city AS pickupCity,
                   t.[status] AS tripStatus,
                   c.licensePlate AS licensePlate, coachType.coachTypeName AS coachTypeName,
                   driver.staffName AS driverName, driver.phone AS driverPhone,
                   driver.cccd AS driverCccd, attendant.staffName AS attendantName,
                   attendant.phone AS attendantPhone, attendant.cccd AS attendantCccd,
                   stops.stopSummary AS stopSummary,
                   CAST(COALESCE(cargo.usedCargoVolume, 0) AS DECIMAL(12,3)) AS usedCargoVolume
            FROM trip t
            JOIN route r ON r.routeId = t.routeId
            JOIN coach c ON c.coachId = t.coachId
            JOIN coach_type coachType ON coachType.coachTypeId = c.coachTypeId
            JOIN route_stop agencyRouteStop ON agencyRouteStop.routeId = t.routeId
            JOIN route_stop originRouteStop ON originRouteStop.routeId = t.routeId
                 AND originRouteStop.stopOrder = (
                     SELECT MIN(firstStop.stopOrder)
                     FROM route_stop firstStop
                     WHERE firstStop.routeId = t.routeId
                 )
            JOIN coach_stop originStop ON originStop.stopPointId = originRouteStop.stopPointId
            JOIN route_stop destinationRouteStop ON destinationRouteStop.routeId = t.routeId
                 AND destinationRouteStop.stopOrder = (
                     SELECT MAX(lastStop.stopOrder)
                     FROM route_stop lastStop
                     WHERE lastStop.routeId = t.routeId
                 )
            JOIN coach_stop destinationStop
              ON destinationStop.stopPointId = destinationRouteStop.stopPointId
            JOIN ticket_agency agency ON agency.stopPointId = agencyRouteStop.stopPointId
                                      AND agency.isActive = 1
            JOIN coach_stop agencyStop ON agencyStop.stopPointId = agency.stopPointId
            JOIN staff currentStaff ON currentStaff.ticketAgencyId = agency.ticketAgencyId
                                   AND currentStaff.accountId = :accountId
                                   AND currentStaff.isActive = 1
            LEFT JOIN staff driver ON driver.staffId = t.driverId
            LEFT JOIN staff attendant ON attendant.staffId = t.attendantId
            OUTER APPLY (
                SELECT STRING_AGG(CONVERT(NVARCHAR(MAX), orderedStops.stopPointName), N' → ')
                       WITHIN GROUP (ORDER BY orderedStops.stopOrder) AS stopSummary
                FROM (
                    SELECT rs.stopOrder, cs.stopPointName
                    FROM route_stop rs
                    JOIN coach_stop cs ON cs.stopPointId = rs.stopPointId
                    WHERE rs.routeId = t.routeId
                      AND rs.stopOrder >= agencyRouteStop.stopOrder
                ) orderedStops
            ) stops
            OUTER APPLY (
                SELECT SUM(ctd.dimensionVol * ctd.quantity) AS usedCargoVolume
                FROM cargo_ticket ct
                JOIN cargo_ticket_detail ctd ON ctd.cargoTicketId = ct.cargoTicketId
                WHERE ct.tripId = t.tripId
                  AND ct.[status] NOT IN ('CANCELLED', 'REJECTED', 'ABANDONED')
            ) cargo
            WHERE t.[status] IN ('SCHEDULED', 'IN_PROGRESS')
              AND originStop.city = agencyStop.city
              AND DATEADD(MINUTE, agencyRouteStop.minutesFromStart, t.departureTime) >= GETDATE()
              AND CAST(DATEADD(MINUTE, agencyRouteStop.minutesFromStart, t.departureTime) AS DATE)
                    = CAST(GETDATE() AS DATE)
              AND COALESCE(cargo.usedCargoVolume, 0) < 2.50
            ORDER BY DATEADD(MINUTE, agencyRouteStop.minutesFromStart, t.departureTime), t.tripId
            """, countQuery = """
            SELECT COUNT(t.tripId)
            FROM trip t
            JOIN route_stop agencyRouteStop ON agencyRouteStop.routeId = t.routeId
            JOIN coach_stop agencyStop ON agencyStop.stopPointId = agencyRouteStop.stopPointId
            JOIN route_stop originRouteStop ON originRouteStop.routeId = t.routeId
                 AND originRouteStop.stopOrder = (
                     SELECT MIN(firstStop.stopOrder)
                     FROM route_stop firstStop
                     WHERE firstStop.routeId = t.routeId
                 )
            JOIN coach_stop originStop ON originStop.stopPointId = originRouteStop.stopPointId
            JOIN ticket_agency agency ON agency.stopPointId = agencyRouteStop.stopPointId
                                      AND agency.isActive = 1
            JOIN staff currentStaff ON currentStaff.ticketAgencyId = agency.ticketAgencyId
                                   AND currentStaff.accountId = :accountId
                                   AND currentStaff.isActive = 1
            WHERE t.[status] IN ('SCHEDULED', 'IN_PROGRESS')
              AND originStop.city = agencyStop.city
              AND DATEADD(MINUTE, agencyRouteStop.minutesFromStart, t.departureTime) >= GETDATE()
              AND CAST(DATEADD(MINUTE, agencyRouteStop.minutesFromStart, t.departureTime) AS DATE)
                    = CAST(GETDATE() AS DATE)
              AND COALESCE((
                    SELECT SUM(detail.dimensionVol * detail.quantity)
                    FROM cargo_ticket cargo
                    JOIN cargo_ticket_detail detail
                      ON detail.cargoTicketId = cargo.cargoTicketId
                    WHERE cargo.tripId = t.tripId
                      AND cargo.[status] NOT IN ('CANCELLED', 'REJECTED', 'ABANDONED')
              ), 0) < 2.50
            """, nativeQuery = true)
    Page<CargoOperationalTripProjection> findUpcomingCargoOperationalTrips(
            @Param("accountId") int accountId,
            Pageable pageable);

    /**
     * Revalidates a cargo assignment at write time. Exact stop IDs are used;
     * matching another office merely because it is in the same city is forbidden.
     */
    @Query(value = """
            SELECT CASE WHEN EXISTS (
                SELECT 1
                FROM trip t WITH (UPDLOCK, HOLDLOCK)
                JOIN route_stop pickup ON pickup.routeId = t.routeId
                JOIN ticket_agency agency ON agency.stopPointId = pickup.stopPointId
                                         AND agency.ticketAgencyId = :ticketAgencyId
                                         AND agency.isActive = 1
                JOIN coach_stop agencyStop ON agencyStop.stopPointId = agency.stopPointId
                JOIN route_stop originRouteStop ON originRouteStop.routeId = t.routeId
                     AND originRouteStop.stopOrder = (
                         SELECT MIN(firstStop.stopOrder)
                         FROM route_stop firstStop
                         WHERE firstStop.routeId = t.routeId
                     )
                JOIN coach_stop originStop ON originStop.stopPointId = originRouteStop.stopPointId
                JOIN route_stop dropoff ON dropoff.routeId = t.routeId
                                       AND dropoff.stopPointId = :dropoffStopId
                                       AND dropoff.stopOrder > pickup.stopOrder
                WHERE t.tripId = :tripId
                  AND pickup.stopPointId = :pickupStopId
                  AND originStop.city = agencyStop.city
                  AND t.[status] = 'SCHEDULED'
                  AND DATEADD(MINUTE, pickup.minutesFromStart, t.departureTime) >= GETDATE()
                  AND CAST(DATEADD(MINUTE, pickup.minutesFromStart, t.departureTime) AS DATE)
                        = CAST(GETDATE() AS DATE)
                  AND COALESCE((
                        SELECT SUM(detail.dimensionVol * detail.quantity)
                        FROM cargo_ticket cargo
                        JOIN cargo_ticket_detail detail
                          ON detail.cargoTicketId = cargo.cargoTicketId
                        WHERE cargo.tripId = t.tripId
                          AND cargo.[status] NOT IN ('CANCELLED', 'REJECTED', 'ABANDONED')
                  ), 0) < 2.50
            ) THEN CAST(1 AS BIT) ELSE CAST(0 AS BIT) END
            """, nativeQuery = true)
    boolean isEligibleForAgencyCargo(
            @Param("tripId") int tripId,
            @Param("pickupStopId") int pickupStopId,
            @Param("dropoffStopId") int dropoffStopId,
            @Param("ticketAgencyId") int ticketAgencyId);

    @Query(value = """
            WITH eligible_trip AS (
                SELECT t.tripId, t.routeId, t.coachId, t.departureTime, t.[status],
                       MAX(pickup.minutesFromStart) AS pickupMinutes
                FROM trip t
                JOIN route_stop pickup ON pickup.routeId = t.routeId
                JOIN coach_stop pickupPoint ON pickupPoint.stopPointId = pickup.stopPointId
                JOIN coach_stop selectedPickup ON selectedPickup.stopPointId = :pickupStopId
                JOIN route_stop dropoff ON dropoff.routeId = t.routeId AND pickup.stopOrder < dropoff.stopOrder
                JOIN coach_stop dropoffPoint ON dropoffPoint.stopPointId = dropoff.stopPointId
                JOIN coach_stop selectedDropoff ON selectedDropoff.stopPointId = :dropoffStopId
                WHERE pickupPoint.city = selectedPickup.city
                  AND dropoffPoint.city = selectedDropoff.city
                  AND t.[status] = 'SCHEDULED'
                GROUP BY t.tripId, t.routeId, t.coachId, t.departureTime, t.[status]
            )
            SELECT e.tripId AS tripId, r.routeName AS routeName,
                   e.departureTime AS departureTime, c.licensePlate AS licensePlate,
                   e.[status] AS tripStatus,
                   DATEADD(MINUTE, e.pickupMinutes, e.departureTime) AS pickupTime,
                   :pickupStopId AS pickupStopId, :dropoffStopId AS dropoffStopId
            FROM eligible_trip e
            JOIN route r ON r.routeId = e.routeId
            JOIN coach c ON c.coachId = e.coachId
            WHERE DATEADD(MINUTE, e.pickupMinutes, e.departureTime) >= GETDATE()
            ORDER BY DATEADD(MINUTE, e.pickupMinutes, e.departureTime)
            """, nativeQuery = true)
    java.util.List<CargoTicketTripOptionProjection> findCargoTicketTripOptions(
            @Param("pickupStopId") int pickupStopId,
            @Param("dropoffStopId") int dropoffStopId);

    @Query(value = """
            WITH eligible_trip AS (
                SELECT t.tripId, t.routeId, t.coachId, t.departureTime, t.[status],
                       MAX(pickup.minutesFromStart) AS pickupMinutes
                FROM trip t
                JOIN route_stop pickup ON pickup.routeId = t.routeId
                JOIN coach_stop pickupPoint ON pickupPoint.stopPointId = pickup.stopPointId
                JOIN coach_stop selectedPickup ON selectedPickup.stopPointId = :pickupStopId
                JOIN route_stop dropoff ON dropoff.routeId = t.routeId AND pickup.stopOrder < dropoff.stopOrder
                JOIN coach_stop dropoffPoint ON dropoffPoint.stopPointId = dropoff.stopPointId
                JOIN coach_stop selectedDropoff ON selectedDropoff.stopPointId = :dropoffStopId
                WHERE pickupPoint.city = selectedPickup.city
                  AND dropoffPoint.city = selectedDropoff.city
                  AND t.[status] = 'SCHEDULED'
                GROUP BY t.tripId, t.routeId, t.coachId, t.departureTime, t.[status]
            )
            SELECT e.tripId AS tripId, r.routeName AS routeName,
                   e.departureTime AS departureTime, c.licensePlate AS licensePlate,
                   ct.coachTypeName AS coachTypeName,
                   e.[status] AS tripStatus,
                   DATEADD(MINUTE, e.pickupMinutes, e.departureTime) AS pickupTime,
                   :pickupStopId AS pickupStopId, :dropoffStopId AS dropoffStopId
            FROM eligible_trip e
            JOIN route r ON r.routeId = e.routeId
            JOIN coach c ON c.coachId = e.coachId
            JOIN coach_type ct ON c.coachTypeId = ct.coachTypeId
            WHERE DATEADD(MINUTE, e.pickupMinutes, e.departureTime) >= GETDATE()
            ORDER BY DATEADD(MINUTE, e.pickupMinutes, e.departureTime)
            """, nativeQuery = true)
    java.util.List<CargoTicketTripOptionWithCoachTypeProjection> findCargoTicketTripOptionsWithCoachType(
            @Param("pickupStopId") int pickupStopId,
            @Param("dropoffStopId") int dropoffStopId);

    @Query(value = """
            SELECT CASE WHEN EXISTS (
                SELECT 1
            FROM trip t
            JOIN route_stop pickup ON pickup.routeId = t.routeId
            JOIN coach_stop pickupPoint ON pickupPoint.stopPointId = pickup.stopPointId
            JOIN coach_stop selectedPickup ON selectedPickup.stopPointId = :pickupStopId
            JOIN route_stop dropoff ON dropoff.routeId = t.routeId AND pickup.stopOrder < dropoff.stopOrder
            JOIN coach_stop dropoffPoint ON dropoffPoint.stopPointId = dropoff.stopPointId
            JOIN coach_stop selectedDropoff ON selectedDropoff.stopPointId = :dropoffStopId
                WHERE t.tripId = :tripId
              AND pickupPoint.city = selectedPickup.city
              AND dropoffPoint.city = selectedDropoff.city
              AND DATEADD(MINUTE, pickup.minutesFromStart, t.departureTime) >= GETDATE()
              AND t.[status] = 'SCHEDULED'
            ) THEN CAST(1 AS BIT) ELSE CAST(0 AS BIT) END
            """, nativeQuery = true)
    boolean isEligibleForCargo(@Param("tripId") int tripId,
            @Param("pickupStopId") int pickupStopId,
            @Param("dropoffStopId") int dropoffStopId);

    /***
     * insertTrip: this method use to insert new trip from user
     * 
     * @param routeId       from FE
     * @param coachId       from FE
     * @param departureTime from FE
     * @param status        from FE
     * @param driverId      from FE
     * @param attendantId   from FEs
     */
    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO [trip] (routeId, coachId, departureTime, [status], driverId, attendantId)
            VALUES (:routeId, :coachId, :departureTime, :status, :driverId, :attendantId)
            """, nativeQuery = true)
    void insertTrip(
            @Param("routeId") Integer routeId,
            @Param("coachId") Integer coachId,
            @Param("departureTime") LocalDateTime departureTime,
            @Param("status") String status,
            @Param("driverId") Integer driverId,
            @Param("attendantId") Integer attendantId);

    /**
     * Atomically updates a scheduled trip using only statuses accepted by
     * CK_Trip_Status.
     *
     * @return one when updated, otherwise zero
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE trip
            SET routeId = :routeId, coachId = :coachId,
                departureTime = :departureTime, [status] = :status,
                driverId = :driverId, attendantId = :attendantId,
                updatedAt = GETDATE()
            WHERE tripId = :tripId
              AND [status] = 'SCHEDULED'
            """, nativeQuery = true)
    int updateScheduledTrip(
            @Param("tripId") Integer tripId,
            @Param("routeId") Integer routeId,
            @Param("coachId") Integer coachId,
            @Param("departureTime") LocalDateTime departureTime,
            @Param("status") String status,
            @Param("driverId") Integer driverId,
            @Param("attendantId") Integer attendantId);

    /**
     * Soft-deletes a scheduled trip as CANCELLED. Physical deletion is forbidden by
     * ticket and seat foreign keys and would destroy operational history.
     *
     * @return one when cancelled, otherwise zero
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE trip
            SET [status] = 'CANCELLED', updatedAt = GETDATE()
            WHERE tripId = :tripId
              AND [status] = 'SCHEDULED'
            """, nativeQuery = true)
    int cancelScheduledTrip(@Param("tripId") Integer tripId);

    /**
     * Searches customer trips with optional time, coach type, and price filters.
     * Seat availability is counted directly from the concrete trip_seat
     * snapshot for each trip. Price is resolved by the trip departure time so
     * old or future trips do not disappear because today's price row changed.
     * Trips whose departure time has already passed are excluded using the
     * request timestamp supplied by the service. A customer result must have at
     * least one active AVAILABLE seat, so sold-out and fully deactivated coaches
     * never reach the selection screen.
     */
    @Query(value = """
            SELECT
                t.tripId AS tripId,
                ct.coachTypeName AS coachTypeName,
                r.routeName AS routeName,
                t.departureTime AS departureTime,
                DATEADD(MINUTE, 432, t.departureTime) AS arrivalTime,
                N'7 giờ 12 phút' AS duration,
                ctp.seatPrice AS seatPrice,
                COALESCE(seat_counts.availableSeats, 0) AS availableSeats,
                COALESCE(seat_counts.totalSeats, 0) AS totalSeats
            FROM trip t
            JOIN route r ON t.routeId = r.routeId
            JOIN coach c ON t.coachId = c.coachId
            JOIN coach_type ct ON c.coachTypeId = ct.coachTypeId
            CROSS APPLY (
                SELECT TOP (1) effective_price.seatPrice
                FROM coach_type_price effective_price
                WHERE effective_price.coachTypeId = ct.coachTypeId
                  AND t.departureTime BETWEEN effective_price.startEffectiveDate AND effective_price.endEffectiveDate
                ORDER BY effective_price.startEffectiveDate DESC, effective_price.coachTypePriceId DESC
            ) ctp
            LEFT JOIN (
                SELECT
                    ts.tripId,
                    CAST(SUM(CASE WHEN UPPER(LTRIM(RTRIM(ts.[status]))) = 'AVAILABLE' THEN 1 ELSE 0 END) AS INT) AS availableSeats,
                    CAST(COUNT(ts.tripSeatId) AS INT) AS totalSeats
                FROM trip_seat ts
                JOIN seat s ON s.seatId = ts.seatId
                WHERE s.isActive = 1
                GROUP BY ts.tripId
            ) seat_counts ON seat_counts.tripId = t.tripId
            WHERE r.routeName = :route
              AND t.departureTime BETWEEN :start AND :end
              AND t.departureTime >= :currentTime
              AND t.[status] = 'SCHEDULED'
              AND COALESCE(seat_counts.availableSeats, 0) > 0
              AND (:checkTimeSlots = 0 OR (
                  (:slot1StartMinute IS NOT NULL AND DATEDIFF(MINUTE, CAST(t.departureTime AS DATE), t.departureTime) BETWEEN :slot1StartMinute AND :slot1EndMinute) OR
                  (:slot2StartMinute IS NOT NULL AND DATEDIFF(MINUTE, CAST(t.departureTime AS DATE), t.departureTime) BETWEEN :slot2StartMinute AND :slot2EndMinute) OR
                  (:slot3StartMinute IS NOT NULL AND DATEDIFF(MINUTE, CAST(t.departureTime AS DATE), t.departureTime) BETWEEN :slot3StartMinute AND :slot3EndMinute) OR
                  (:slot4StartMinute IS NOT NULL AND DATEDIFF(MINUTE, CAST(t.departureTime AS DATE), t.departureTime) BETWEEN :slot4StartMinute AND :slot4EndMinute)
              ))
              AND (:checkLayouts = 0 OR (
                  LOWER(ct.coachTypeName) LIKE LOWER(:layoutKeyword1) OR
                  LOWER(ct.coachTypeName) LIKE LOWER(:layoutKeyword2) OR
                  LOWER(ct.coachTypeName) LIKE LOWER(:layoutKeyword3)
              ))
              AND (:minPrice IS NULL OR ctp.seatPrice >= :minPrice)
              AND (:maxPrice IS NULL OR ctp.seatPrice <= :maxPrice)
            ORDER BY t.departureTime
            """, countQuery = """
            SELECT COUNT(t.tripId)
            FROM trip t
            JOIN route r ON t.routeId = r.routeId
            JOIN coach c ON t.coachId = c.coachId
            JOIN coach_type ct ON c.coachTypeId = ct.coachTypeId
            CROSS APPLY (
                SELECT TOP (1) effective_price.seatPrice
                FROM coach_type_price effective_price
                WHERE effective_price.coachTypeId = ct.coachTypeId
                  AND t.departureTime BETWEEN effective_price.startEffectiveDate AND effective_price.endEffectiveDate
                ORDER BY effective_price.startEffectiveDate DESC, effective_price.coachTypePriceId DESC
            ) ctp
            WHERE r.routeName = :route
              AND t.departureTime BETWEEN :start AND :end
              AND t.departureTime >= :currentTime
              AND t.[status] = 'SCHEDULED'
              AND EXISTS (
                  SELECT 1
                  FROM trip_seat available_ts
                  JOIN seat available_s ON available_s.seatId = available_ts.seatId
                  WHERE available_ts.tripId = t.tripId
                    AND available_s.isActive = 1
                    AND UPPER(LTRIM(RTRIM(available_ts.[status]))) = 'AVAILABLE'
              )
              AND (:checkTimeSlots = 0 OR (
                  (:slot1StartMinute IS NOT NULL AND DATEDIFF(MINUTE, CAST(t.departureTime AS DATE), t.departureTime) BETWEEN :slot1StartMinute AND :slot1EndMinute) OR
                  (:slot2StartMinute IS NOT NULL AND DATEDIFF(MINUTE, CAST(t.departureTime AS DATE), t.departureTime) BETWEEN :slot2StartMinute AND :slot2EndMinute) OR
                  (:slot3StartMinute IS NOT NULL AND DATEDIFF(MINUTE, CAST(t.departureTime AS DATE), t.departureTime) BETWEEN :slot3StartMinute AND :slot3EndMinute) OR
                  (:slot4StartMinute IS NOT NULL AND DATEDIFF(MINUTE, CAST(t.departureTime AS DATE), t.departureTime) BETWEEN :slot4StartMinute AND :slot4EndMinute)
              ))
              AND (:checkLayouts = 0 OR (
                  LOWER(ct.coachTypeName) LIKE LOWER(:layoutKeyword1) OR
                  LOWER(ct.coachTypeName) LIKE LOWER(:layoutKeyword2) OR
                  LOWER(ct.coachTypeName) LIKE LOWER(:layoutKeyword3)
              ))
              AND (:minPrice IS NULL OR ctp.seatPrice >= :minPrice)
              AND (:maxPrice IS NULL OR ctp.seatPrice <= :maxPrice)
            """, nativeQuery = true)
    Page<TripFilterProjection> filterTrips(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("currentTime") LocalDateTime currentTime,
            @Param("route") String route,
            @Param("checkTimeSlots") int checkTimeSlots,
            @Param("slot1StartMinute") Integer slot1StartMinute, @Param("slot1EndMinute") Integer slot1EndMinute,
            @Param("slot2StartMinute") Integer slot2StartMinute, @Param("slot2EndMinute") Integer slot2EndMinute,
            @Param("slot3StartMinute") Integer slot3StartMinute, @Param("slot3EndMinute") Integer slot3EndMinute,
            @Param("slot4StartMinute") Integer slot4StartMinute, @Param("slot4EndMinute") Integer slot4EndMinute,
            @Param("checkLayouts") int checkLayouts,
            @Param("layoutKeyword1") String layoutKeyword1,
            @Param("layoutKeyword2") String layoutKeyword2,
            @Param("layoutKeyword3") String layoutKeyword3,
            @Param("minPrice") Double minPrice,
            @Param("maxPrice") Double maxPrice,
            Pageable pageable);

    /**
     * Loads the default customer trip list for a route/date before any filters
     * are selected. The projection shape intentionally matches filterTrips so
     * the frontend does not need a separate placeholder mapping. Departed trips
     * and trips without an active AVAILABLE seat are excluded using the same
     * rules as filtered search.
     */
    @Query(value = """
            SELECT
                t.tripId AS tripId,
                ct.coachTypeName AS coachTypeName,
                r.routeName AS routeName,
                ct.seatLayout AS seatLayoutName,
                t.[status] AS status,
                t.departureTime AS departureTime,
                DATEADD(MINUTE, 432, t.departureTime) AS arrivalTime,
                N'7 giờ 12 phút' AS duration,
                ctp.seatPrice AS seatPrice,
                COALESCE(seat_counts.availableSeats, 0) AS availableSeats,
                COALESCE(seat_counts.totalSeats, 0) AS totalSeats
            FROM trip t
            JOIN route r ON t.routeId = r.routeId
            JOIN coach c ON t.coachId = c.coachId
            JOIN coach_type ct ON c.coachTypeId = ct.coachTypeId
            CROSS APPLY (
                SELECT TOP (1) effective_price.seatPrice
                FROM coach_type_price effective_price
                WHERE effective_price.coachTypeId = ct.coachTypeId
                  AND t.departureTime BETWEEN effective_price.startEffectiveDate AND effective_price.endEffectiveDate
                ORDER BY effective_price.startEffectiveDate DESC, effective_price.coachTypePriceId DESC
            ) ctp
            LEFT JOIN (
                SELECT
                    ts.tripId,
                    CAST(SUM(CASE WHEN UPPER(LTRIM(RTRIM(ts.[status]))) = 'AVAILABLE' THEN 1 ELSE 0 END) AS INT) AS availableSeats,
                    CAST(COUNT(ts.tripSeatId) AS INT) AS totalSeats
                FROM trip_seat ts
                JOIN seat s ON s.seatId = ts.seatId
                WHERE s.isActive = 1
                GROUP BY ts.tripId
            ) seat_counts ON seat_counts.tripId = t.tripId
            WHERE t.departureTime BETWEEN :start AND :end
              AND t.departureTime >= :currentTime
              AND r.routeName = :route
              AND t.[status] = 'SCHEDULED'
              AND COALESCE(seat_counts.availableSeats, 0) > 0
            ORDER BY t.departureTime
            """, countQuery = """
            SELECT COUNT(t.tripId)
            FROM trip t
            JOIN route r ON t.routeId = r.routeId
            JOIN coach c ON t.coachId = c.coachId
            JOIN coach_type ct ON c.coachTypeId = ct.coachTypeId
            CROSS APPLY (
                SELECT TOP (1) effective_price.seatPrice
                FROM coach_type_price effective_price
                WHERE effective_price.coachTypeId = ct.coachTypeId
                  AND t.departureTime BETWEEN effective_price.startEffectiveDate AND effective_price.endEffectiveDate
                ORDER BY effective_price.startEffectiveDate DESC, effective_price.coachTypePriceId DESC
            ) ctp
            WHERE t.departureTime BETWEEN :start AND :end
              AND t.departureTime >= :currentTime
              AND r.routeName = :route
              AND t.[status] = 'SCHEDULED'
              AND EXISTS (
                  SELECT 1
                  FROM trip_seat available_ts
                  JOIN seat available_s ON available_s.seatId = available_ts.seatId
                  WHERE available_ts.tripId = t.tripId
                    AND available_s.isActive = 1
                    AND UPPER(LTRIM(RTRIM(available_ts.[status]))) = 'AVAILABLE'
              )
            """, nativeQuery = true)
    Page<TripDetailProjection> findTripDetails(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("currentTime") LocalDateTime currentTime,
            @Param("route") String route,
            Pageable pageable);

    /**
     * Returns the manager trip table for one day, optionally narrowed by route and
     * half-day. Route and crew identifiers are included so the edit form can be
     * populated without a second database-shaped details request.
     */
    @Query(value = """
            SELECT
                t.tripId AS tripId,
                r.routeId AS routeId,
                r.routeName AS routeName,
                c.coachId AS coachId,
                t.driverId AS driverId,
                driver.staffName AS driverName,
                driver.phone AS driverPhone,
                t.attendantId AS attendantId,
                attendant.staffName AS attendantName,
                attendant.phone AS attendantPhone,
                t.[status] AS tripStatus,
                c.manufacturer AS manufacturer,
                ct.coachTypeName AS coachTypeName,
                c.licensePlate AS licensePlate,
                c.[status] AS coachStatus,
                CAST(t.departureTime AS DATE) AS departureDate,
                CAST(t.departureTime AS TIME) AS departureTime,
                COALESCE(seat_counts.availableSeats, 0) AS availableSeats,
                COALESCE(seat_counts.totalSeats, 0) AS totalSeats,
                CAST(COALESCE(cargo_load.usedCargoVolume, 0) AS DECIMAL(12,3)) AS usedCargoVolume,
                CAST(2.50 AS DECIMAL(12,3)) AS cargoCapacity
            FROM trip t
            LEFT JOIN route r ON t.routeId = r.routeId
            LEFT JOIN coach c ON t.coachId = c.coachId
            LEFT JOIN coach_type ct ON c.coachTypeId = ct.coachTypeId
            LEFT JOIN staff driver ON driver.staffId = t.driverId
            LEFT JOIN staff attendant ON attendant.staffId = t.attendantId
            LEFT JOIN (
                SELECT
                    ts.tripId,
                    CAST(SUM(CASE WHEN UPPER(LTRIM(RTRIM(ts.[status]))) = 'AVAILABLE' THEN 1 ELSE 0 END) AS INT) AS availableSeats,
                    CAST(COUNT(ts.tripSeatId) AS INT) AS totalSeats
                FROM trip_seat ts
                JOIN seat s ON s.seatId = ts.seatId
                WHERE s.isActive = 1
                GROUP BY ts.tripId
            ) seat_counts ON seat_counts.tripId = t.tripId
            OUTER APPLY (
                SELECT SUM(ctd.dimensionVol * ctd.quantity) AS usedCargoVolume
                FROM cargo_ticket cargo
                JOIN cargo_ticket_detail ctd ON ctd.cargoTicketId = cargo.cargoTicketId
                WHERE cargo.tripId = t.tripId
                  AND cargo.[status] NOT IN ('CANCELLED', 'REJECTED', 'ABANDONED')
            ) cargo_load
            WHERE CAST(t.departureTime AS DATE) = :departureDate
              AND (:routeId IS NULL OR t.routeId = :routeId)
              AND (:status IS NULL OR t.[status] = :status)
              AND (:period IS NULL
                   OR (:period = 'MORNING' AND CAST(t.departureTime AS TIME) < '12:00:00')
                   OR (:period = 'EVENING' AND CAST(t.departureTime AS TIME) >= '12:00:00'))
            ORDER BY CASE WHEN c.[status] = 'HAVE_INCIDENT' THEN 0 ELSE 1 END,
                     t.departureTime ASC,
                     t.tripId ASC
            """, countQuery = """
            SELECT COUNT(*)
            FROM trip t
            WHERE CAST(t.departureTime AS DATE) = :departureDate
              AND (:routeId IS NULL OR t.routeId = :routeId)
              AND (:status IS NULL OR t.[status] = :status)
              AND (:period IS NULL
                   OR (:period = 'MORNING' AND CAST(t.departureTime AS TIME) < '12:00:00')
                   OR (:period = 'EVENING' AND CAST(t.departureTime AS TIME) >= '12:00:00'))
            """, nativeQuery = true)
    Page<TripSummaryProjection> viewAllTripSummaries(
            @Param("departureDate") String departureDate,
            @Param("routeId") Integer routeId,
            @Param("period") String period,
            @Param("status") String status,
            Pageable pageable);

    /**
     * Finds upcoming trips for the ticket-staff "view trip info" screen.
     *
     * <p>
     * The screen is operational, so results are scoped to the selected day
     * using an inclusive start and exclusive next-day boundary. Past days are
     * blocked by the service, but every trip inside the selected day remains
     * visible, including trips whose departure time already passed. City is
     * derived from the route name because the current schema does not store a
     * trip-level city. Keep this query separate from customer trip search; it
     * exposes staff-only fields such as coach license plate and crew names.
     * </p>
     */
    @Query(value = """
            SELECT
                t.tripId AS tripId,
                city_info.departureCity AS departureCity,
                ct.coachTypeName AS coachTypeName,
                r.routeName AS routeName,
                ct.seatLayout AS seatLayoutName,
                t.[status] AS status,
                t.departureTime AS departureTime,
                DATEADD(MINUTE, 432, t.departureTime) AS arrivalTime,
                N'7 giờ 12 phút' AS duration,
                ctp.seatPrice AS seatPrice,
                COALESCE(seat_counts.availableSeats, 0) AS availableSeats,
                COALESCE(seat_counts.totalSeats, 0) AS totalSeats,
                c.licensePlate AS licensePlate,
                driver.staffName AS driverName,
                attendant.staffName AS attendantName
            FROM trip t
            JOIN route r ON t.routeId = r.routeId
            JOIN coach c ON t.coachId = c.coachId
            JOIN coach_type ct ON c.coachTypeId = ct.coachTypeId
            JOIN coach_type_price ctp ON ct.coachTypeId = ctp.coachTypeId
                AND t.departureTime BETWEEN ctp.startEffectiveDate AND ctp.endEffectiveDate
            LEFT JOIN staff driver ON driver.staffId = t.driverId
            LEFT JOIN staff attendant ON attendant.staffId = t.attendantId
            CROSS APPLY (
                SELECT CASE
                    WHEN CHARINDEX(N'→', r.routeName) > 0
                        THEN LTRIM(RTRIM(SUBSTRING(r.routeName, 1, CHARINDEX(N'→', r.routeName) - 1)))
                    WHEN CHARINDEX(N' - ', r.routeName) > 0
                        THEN LTRIM(RTRIM(SUBSTRING(r.routeName, 1, CHARINDEX(N' - ', r.routeName) - 1)))
                    ELSE LTRIM(RTRIM(r.routeName))
                END AS departureCity
            ) city_info
            LEFT JOIN (
                SELECT
                    ts.tripId,
                    CAST(SUM(CASE WHEN UPPER(LTRIM(RTRIM(ts.[status]))) = 'AVAILABLE' THEN 1 ELSE 0 END) AS INT) AS availableSeats,
                    CAST(COUNT(ts.tripSeatId) AS INT) AS totalSeats
                FROM trip_seat ts
                JOIN seat s ON s.seatId = ts.seatId
                WHERE s.isActive = 1
                GROUP BY ts.tripId
            ) seat_counts ON seat_counts.tripId = t.tripId
            WHERE t.departureTime >= CAST(:departureDate AS DATETIME2)
              AND t.departureTime < CAST(:nextDepartureDate AS DATETIME2)
              AND (:city IS NULL OR city_info.departureCity = :city)
              AND (:timeFromMinute IS NULL OR DATEDIFF(MINUTE, CAST(t.departureTime AS DATE), t.departureTime) >= :timeFromMinute)
              AND (:timeToMinute IS NULL OR DATEDIFF(MINUTE, CAST(t.departureTime AS DATE), t.departureTime) <= :timeToMinute)
              AND (:coachTypeKeyword IS NULL OR LOWER(ct.coachTypeName) LIKE LOWER(:coachTypeKeyword))
              AND (:checkPrices = 0 OR (
                  (:priceLow = 1 AND ctp.seatPrice < 300000) OR
                  (:priceMiddle = 1 AND ctp.seatPrice >= 300000 AND ctp.seatPrice <= 500000) OR
                  (:priceHigh = 1 AND ctp.seatPrice > 500000)
              ))
              AND (:checkStatuses = 0 OR t.[status] IN (:statuses))
              AND (:driverName IS NULL OR LOWER(driver.staffName) LIKE LOWER(CONCAT('%', :driverName, '%')))
            ORDER BY city_info.departureCity ASC, t.departureTime ASC
            """, countQuery = """
            SELECT COUNT(t.tripId)
            FROM trip t
            JOIN route r ON t.routeId = r.routeId
            JOIN coach c ON t.coachId = c.coachId
            JOIN coach_type ct ON c.coachTypeId = ct.coachTypeId
            JOIN coach_type_price ctp ON ct.coachTypeId = ctp.coachTypeId
                AND t.departureTime BETWEEN ctp.startEffectiveDate AND ctp.endEffectiveDate
            LEFT JOIN staff driver ON driver.staffId = t.driverId
            CROSS APPLY (
                SELECT CASE
                    WHEN CHARINDEX(N'→', r.routeName) > 0
                        THEN LTRIM(RTRIM(SUBSTRING(r.routeName, 1, CHARINDEX(N'→', r.routeName) - 1)))
                    WHEN CHARINDEX(N' - ', r.routeName) > 0
                        THEN LTRIM(RTRIM(SUBSTRING(r.routeName, 1, CHARINDEX(N' - ', r.routeName) - 1)))
                    ELSE LTRIM(RTRIM(r.routeName))
                END AS departureCity
            ) city_info
            WHERE t.departureTime >= CAST(:departureDate AS DATETIME2)
              AND t.departureTime < CAST(:nextDepartureDate AS DATETIME2)
              AND (:city IS NULL OR city_info.departureCity = :city)
              AND (:timeFromMinute IS NULL OR DATEDIFF(MINUTE, CAST(t.departureTime AS DATE), t.departureTime) >= :timeFromMinute)
              AND (:timeToMinute IS NULL OR DATEDIFF(MINUTE, CAST(t.departureTime AS DATE), t.departureTime) <= :timeToMinute)
              AND (:coachTypeKeyword IS NULL OR LOWER(ct.coachTypeName) LIKE LOWER(:coachTypeKeyword))
              AND (:checkPrices = 0 OR (
                  (:priceLow = 1 AND ctp.seatPrice < 300000) OR
                  (:priceMiddle = 1 AND ctp.seatPrice >= 300000 AND ctp.seatPrice <= 500000) OR
                  (:priceHigh = 1 AND ctp.seatPrice > 500000)
              ))
              AND (:checkStatuses = 0 OR t.[status] IN (:statuses))
              AND (:driverName IS NULL OR LOWER(driver.staffName) LIKE LOWER(CONCAT('%', :driverName, '%')))
            """, nativeQuery = true)
    Page<StaffTripInfoProjection> findStaffTripInfos(
            @Param("departureDate") LocalDate departureDate,
            @Param("nextDepartureDate") LocalDate nextDepartureDate,
            @Param("city") String city,
            @Param("timeFromMinute") Integer timeFromMinute,
            @Param("timeToMinute") Integer timeToMinute,
            @Param("coachTypeKeyword") String coachTypeKeyword,
            @Param("checkPrices") int checkPrices,
            @Param("priceLow") int priceLow,
            @Param("priceMiddle") int priceMiddle,
            @Param("priceHigh") int priceHigh,
            @Param("checkStatuses") int checkStatuses,
            @Param("statuses") List<String> statuses,
            @Param("driverName") String driverName,
            Pageable pageable);

    /**
     * Finds active coaches assigned to the selected route which do not overlap
     * another non-cancelled trip in the standard 7 hour 12 minute trip window.
     */
    @Query(value = """
            SELECT c.coachId AS id, c.licensePlate AS displayName,
                   CONCAT(c.manufacturer, ' - ', ct.coachTypeName) AS secondaryText
            FROM coach c
            JOIN coach_type ct ON ct.coachTypeId = c.coachTypeId
            WHERE c.[status] = 'ACTIVE'
              AND (c.routeId IS NULL OR c.routeId = :routeId)
              AND NOT EXISTS (
                  SELECT 1 FROM trip busy
                  WHERE busy.coachId = c.coachId
                    AND busy.tripId <> COALESCE(:excludeTripId, -1)
                    AND busy.[status] NOT IN ('CANCELED', 'CANCELLED')
                    AND busy.departureTime < DATEADD(MINUTE, 432, :departureTime)
                    AND DATEADD(MINUTE, 432, busy.departureTime) > :departureTime
              )
            ORDER BY c.licensePlate
            """, nativeQuery = true)
    List<TripResourceProjection> findAvailableCoaches(
            @Param("routeId") Integer routeId,
            @Param("departureTime") LocalDateTime departureTime,
            @Param("excludeTripId") Integer excludeTripId);

    /**
     * Incident dispatch is not constrained by the coach's normal route. Every
     * active, conflict-free vehicle with enough seats is a valid rescue coach.
     */
    @Query(value = """
            SELECT c.coachId AS id, c.licensePlate AS displayName,
                   CONCAT(c.manufacturer, ' - ', ct.coachTypeName, ' - ',
                          (SELECT COUNT(*) FROM seat capacity
                           WHERE capacity.coachId = c.coachId AND capacity.isActive = 1), N' chỗ') AS secondaryText
            FROM coach c
            JOIN coach_type ct ON ct.coachTypeId = c.coachTypeId
            WHERE c.[status] = 'ACTIVE'
              AND (SELECT COUNT(*) FROM seat capacity
                   WHERE capacity.coachId = c.coachId AND capacity.isActive = 1) >= :requiredSeats
              AND NOT EXISTS (
                  SELECT 1 FROM trip busy
                  WHERE busy.coachId = c.coachId
                    AND busy.tripId <> :excludeTripId
                    AND busy.[status] NOT IN ('CANCELED', 'CANCELLED')
                    AND busy.departureTime < DATEADD(MINUTE, 432, :departureTime)
                    AND DATEADD(MINUTE, 432, busy.departureTime) > :departureTime
              )
            ORDER BY c.licensePlate
            """, nativeQuery = true)
    List<TripResourceProjection> findIncidentReplacementCoaches(
            @Param("departureTime") LocalDateTime departureTime,
            @Param("excludeTripId") Integer excludeTripId,
            @Param("requiredSeats") Integer requiredSeats);

    /**
     * Finds active trip staff in the requested position with no overlapping trip.
     */
    @Query(value = """
            SELECT s.staffId AS id, s.staffName AS displayName,
                   CONCAT(s.phone, ' - ', s.staffPosition) AS secondaryText
            FROM staff s
            WHERE s.isActive = 1 AND s.staffPosition = :position
              AND NOT EXISTS (
                  SELECT 1 FROM trip busy
                  WHERE (busy.driverId = s.staffId OR busy.attendantId = s.staffId)
                    AND busy.tripId <> COALESCE(:excludeTripId, -1)
                    AND busy.[status] NOT IN ('CANCELED', 'CANCELLED')
                    AND busy.departureTime < DATEADD(MINUTE, 432, :departureTime)
                    AND DATEADD(MINUTE, 432, busy.departureTime) > :departureTime
              )
            ORDER BY s.staffName
            """, nativeQuery = true)
    List<TripResourceProjection> findAvailableStaff(
            @Param("position") String position,
            @Param("departureTime") LocalDateTime departureTime,
            @Param("excludeTripId") Integer excludeTripId);

    /** Counts every passenger who still belongs to the operational manifest. */
    @Query(value = """
            SELECT CAST(COUNT(*) AS INT)
            FROM passenger_ticket ticket
            JOIN passenger_ticket_detail detail
              ON detail.passengerTicketId = ticket.passengerTicketId
            WHERE ticket.tripId = :tripId
              AND ticket.[status] <> 'CANCELLED'
              AND detail.[status] IN ('PENDING', 'CONFIRMED', 'CHECKED_IN')
            """, nativeQuery = true)
    Integer countIncidentManifestPassengers(@Param("tripId") Integer tripId);

    /**
     * A replacement route must still contain every passenger and cargo leg in
     * the same direction. Otherwise keeping the old manifest would be corrupt.
     */
    @Query(value = """
            SELECT CAST(COUNT(*) AS INT)
            FROM (
                SELECT ticket.pickupStopId, ticket.dropoffStopId
                FROM passenger_ticket ticket
                WHERE ticket.tripId = :tripId
                  AND ticket.[status] <> 'CANCELLED'
                UNION ALL
                SELECT cargo.pickupStopId, cargo.dropoffStopId
                FROM cargo_ticket cargo
                WHERE cargo.tripId = :tripId
                  AND cargo.[status] NOT IN ('CANCELLED', 'REJECTED', 'ABANDONED')
            ) manifest
            WHERE NOT EXISTS (
                SELECT 1
                FROM route_stop pickup
                JOIN route_stop dropoff
                  ON dropoff.routeId = pickup.routeId
                 AND dropoff.stopOrder > pickup.stopOrder
                WHERE pickup.routeId = :routeId
                  AND pickup.stopPointId = manifest.pickupStopId
                  AND dropoff.stopPointId = manifest.dropoffStopId
            )
            """, nativeQuery = true)
    Integer countManifestItemsOutsideRoute(
            @Param("tripId") Integer tripId,
            @Param("routeId") Integer routeId);

    /** Resumes the same trip so its passenger and cargo manifests remain attached. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE trip
            SET routeId = :routeId,
                coachId = :coachId,
                driverId = :driverId,
                departureTime = :departureTime,
                [status] = 'IN_PROGRESS',
                updatedAt = GETDATE()
            WHERE tripId = :tripId
              AND [status] = 'IN_PROGRESS'
              AND EXISTS (
                  SELECT 1 FROM coach broken
                  WHERE broken.coachId = trip.coachId
                    AND broken.[status] = 'HAVE_INCIDENT'
              )
            """, nativeQuery = true)
    int dispatchIncidentReplacement(
            @Param("tripId") Integer tripId,
            @Param("routeId") Integer routeId,
            @Param("coachId") Integer coachId,
            @Param("driverId") Integer driverId,
            @Param("departureTime") LocalDateTime departureTime);

    /**
     * Counts available seats for one concrete trip from its trip_seat snapshot.
     * The tripId already owns the coach/date context, so no external route,
     * coach, or date parameter is needed.
     */
    @Query(value = """
            SELECT CAST(COUNT(1) AS INT)
            FROM trip_seat ts
            JOIN seat s ON s.seatId = ts.seatId
            WHERE ts.tripId = :tripId
              AND s.isActive = 1
              AND UPPER(LTRIM(RTRIM(ts.[status]))) = 'AVAILABLE'
            """, nativeQuery = true)
    Integer countAvailableTripSeatsByTripId(@Param("tripId") Integer tripId);

    /**
     * Finds the ordered stop timeline for one concrete trip. The trip id is the
     * source of truth because a coach may serve different routes or directions
     * on the same day. Each stop time is forecast from the trip departure and
     * the route-specific minutes-from-start value.
     *
     * @param tripId concrete scheduled trip identifier
     * @return active route stops in travel order
     */
    @Query(value = """
            SELECT
                t.tripId AS tripId,
                r.routeName AS routeName,
                cs.stopPointId AS stopPointId,
                cs.stopPointName AS stopPointName,
                cs.[address] AS [address],
                cs.city AS city,
                rs.stopOrder AS stopOrder,
                rs.minutesFromStart AS minutesFromStart,
                DATEADD(MINUTE, rs.minutesFromStart, t.departureTime) AS estimatedStopTime
            FROM trip t
            JOIN route r ON r.routeId = t.routeId
            JOIN route_stop rs ON rs.routeId = t.routeId
            JOIN coach_stop cs ON cs.stopPointId = rs.stopPointId
            WHERE t.tripId = :tripId
              AND cs.isActive = 1
            ORDER BY rs.stopOrder ASC
            """, nativeQuery = true)
    List<TripStopProjection> findTripStopsByTripId(@Param("tripId") Integer tripId);

    @Query(value = "SELECT MAX(CAST(departureTime AS DATE)) FROM [trip]", nativeQuery = true)
    LocalDate findMaxDepartureDate();

    @Transactional
    @Procedure(name = "sp_AutoGenerateWeeklySchedule_Final")
    public void autoGenerateWeeklySchedule(@Param("StartDate") String startDate);

    @Query(value = """
                SELECT CASE WHEN EXISTS (
                    SELECT 1 FROM coach c WHERE c.coachId = :coachId AND EXISTS (
                        SELECT 1 FROM trip t WHERE t.coachId = c.coachId
                            AND departureTime >= DATEADD(hour, -8, GETDATE())
                            AND t.status NOT IN ('CANCELLED', 'COMPLETED')
                    )
                ) THEN CAST(1 AS BIT) ELSE CAST(0 AS BIT) END
            """, nativeQuery = true)
    boolean checkIfCoachHasTodoTrips(@Param("coachId") Integer coachId);

    @Query(value = """
                SELECT COUNT(*)
                FROM trip t
                WHERE t.coachId = :coachId
                    AND t.departureTime >= DATEADD(hour, -8, GETDATE())
                    AND t.status NOT IN ('CANCELLED', 'COMPLETED')
            """, nativeQuery = true)
    long countUpcomingTripsByCoachId(@Param("coachId") Integer coachId);

    /**
     * Batch upcoming-trip counts for many coaches (one query, GROUP BY coachId).
     * Coaches with zero matching trips are omitted from the result.
     */
    @Query(value = """
                SELECT t.coachId AS coachId, COUNT(*) AS upcomingCount
                FROM trip t
                WHERE t.coachId IN (:coachIds)
                    AND t.departureTime >= DATEADD(hour, -8, GETDATE())
                    AND t.status NOT IN ('CANCELLED', 'COMPLETED')
                GROUP BY t.coachId
            """, nativeQuery = true)
    List<CoachUpcomingTripCountProjection> countUpcomingTripsGroupedByCoachIds(
            @Param("coachIds") Collection<Integer> coachIds);

    boolean existsByCoach_CoachId(Integer coachId);

    @Query(value = """
            SELECT COUNT(DISTINCT CAST(departureTime AS DATE))
            FROM trip
            WHERE departureTime >= :startOfDay
              AND departureTime <= :endOfPeriod
            """, nativeQuery = true)
    int countDistinctDaysWithSchedule(@Param("startOfDay") LocalDateTime startOfDay,
            @Param("endOfPeriod") LocalDateTime endOfPeriod);

    @Query("""
            SELECT t FROM Trip t
            JOIN FETCH t.route
            JOIN FETCH t.coach c
            JOIN FETCH c.coachType
            WHERE t.tripId = :tripId
            """)
    Optional<Trip> findByIdWithRouteAndCoach(@Param("tripId") Integer tripId);

    @Query(value = """
            SELECT
                t.tripId AS tripId,
                r.routeName AS routeName,
                ct.coachTypeName AS coachTypeName,
                t.departureTime AS departureTime,
                (
                    SELECT TOP 1 ts.price
                    FROM trip_seat ts
                    WHERE ts.tripId = t.tripId AND ts.price IS NOT NULL
                ) AS seatPrice,
                COALESCE(seat_counts.availableSeats, 0) AS availableSeats,
                COALESCE(seat_counts.totalSeats, 0) AS totalSeats
            FROM trip t
            JOIN route r ON t.routeId = r.routeId
            JOIN coach c ON t.coachId = c.coachId
            JOIN coach_type ct ON c.coachTypeId = ct.coachTypeId
            LEFT JOIN (
                SELECT
                    ts.tripId,
                    CAST(SUM(CASE WHEN UPPER(LTRIM(RTRIM(ts.[status]))) = 'AVAILABLE' THEN 1 ELSE 0 END) AS INT) AS availableSeats,
                    CAST(COUNT(ts.tripSeatId) AS INT) AS totalSeats
                FROM trip_seat ts
                GROUP BY ts.tripId
            ) seat_counts ON seat_counts.tripId = t.tripId
            WHERE t.routeId = :routeId
              AND t.departureTime >= :dayStart
              AND t.departureTime <= :dayEnd
              AND t.departureTime > :minDepartureTime
              AND t.[status] = 'SCHEDULED'
              AND (:excludeTripId IS NULL OR t.tripId <> :excludeTripId)
              AND COALESCE(seat_counts.availableSeats, 0) >= :minAvailableSeats
            ORDER BY t.departureTime ASC
            """, nativeQuery = true)
    List<StaffPassengerTransferCandidateProjection> findStaffTransferCandidates(
            @Param("routeId") Integer routeId,
            @Param("dayStart") LocalDateTime dayStart,
            @Param("dayEnd") LocalDateTime dayEnd,
            @Param("minDepartureTime") LocalDateTime minDepartureTime,
            @Param("excludeTripId") Integer excludeTripId,
            @Param("minAvailableSeats") int minAvailableSeats);

    @Query(value = """
            SELECT DISTINCT t.*
            FROM trip t
            JOIN route_stop pickup ON pickup.routeId = t.routeId
            JOIN coach_stop pickupPoint ON pickupPoint.stopPointId = pickup.stopPointId
            JOIN coach_stop selectedPickup ON selectedPickup.stopPointId = :pickupStopId
            JOIN route_stop dropoff ON dropoff.routeId = t.routeId AND pickup.stopOrder < dropoff.stopOrder
            JOIN coach_stop dropoffPoint ON dropoffPoint.stopPointId = dropoff.stopPointId
            JOIN coach_stop selectedDropoff ON selectedDropoff.stopPointId = :dropoffStopId
            WHERE pickupPoint.city = selectedPickup.city
              AND dropoffPoint.city = selectedDropoff.city
              AND DATEADD(MINUTE, pickup.minutesFromStart, t.departureTime) >= GETDATE()
              AND t.[status] IN ('SCHEDULED', 'IN_PROGRESS')
            """, nativeQuery = true)
    List<Trip> findTripsByStopsInOrder(
            @Param("pickupStopId") int pickupStopId,
            @Param("dropoffStopId") int dropoffStopId);
}
