package com.ralsei.repository;

import java.util.List;
import java.math.BigDecimal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ralsei.dto.projection.customer.CargoOrderLookupProjection;
import com.ralsei.dto.projection.customer.CargoOrderStopProjection;
import com.ralsei.model.CargoTicketDetail;

/**
 * Provides persistence access for cargo ticket detail data.
 */
public interface CargoTicketDetailRepository extends JpaRepository<CargoTicketDetail, Integer> {
    List<CargoTicketDetail> findByCargoTicket_CargoTicketId(int cargoTicketId);

    /**
     * Returns occupied cargo volume for active orders assigned to one trip.
     * calculate dimensionVol for the maximum of the dimensions
     */
    @Query(value = """
            SELECT COALESCE(SUM(ctd.dimensionVol * ctd.quantity), 0)
            FROM cargo_ticket_detail ctd
            JOIN cargo_ticket ct ON ct.cargoTicketId = ctd.cargoTicketId
            WHERE ct.tripId = :tripId
              AND ct.[status] NOT IN ('CANCELLED', 'REJECTED', 'ABANDONED')
            """, nativeQuery = true)
    BigDecimal sumActiveVolumeByTripId(@Param("tripId") int tripId);

    /**
     * Returns the sum of per-package volume multiplied by quantity for one order.
     */
    @Query(value = """
            SELECT COALESCE(SUM(ctd.dimensionVol * ctd.quantity), 0)
            FROM cargo_ticket_detail ctd
            WHERE ctd.cargoTicketId = :cargoTicketId
            """, nativeQuery = true)
    @Deprecated
    BigDecimal sumVolumeByCargoTicketId(@Param("cargoTicketId") int cargoTicketId);

    @Query(value = """
            SELECT ct.cargoTicketId AS cargoTicketId,
                   ct.ticketCode AS ticketCode,
                   ct.[status] AS [status],
                   ct.totalPrice AS totalPrice,
                   ct.codAmount AS codAmount,
                   ct.feePayer AS feePayer,
                   ct.[description] AS ticketDescription,
                   ct.createdAt AS bookedAt,
                   t.tripId AS tripId,
                   t.departureTime AS departureTime,
                   r.routeName AS routeName,
                   co.licensePlate AS licensePlate,
                   driver.staffName AS driverName,
                   ta.ticketAgencyName AS ticketAgencyName,
                   pickup.stopPointId AS pickupStopId,
                   pickup.stopPointName AS pickupStopName,
                   pickup.[address] AS pickupAddress,
                   pickup.city AS pickupCity,
                   dropoff.stopPointId AS dropoffStopId,
                   dropoff.stopPointName AS dropoffStopName,
                   dropoff.[address] AS dropoffAddress,
                   dropoff.city AS dropoffCity,
                   ct.senderName AS senderName,
                   ct.senderPhone AS senderPhone,
                   ct.receiverName AS receiverName,
                   ct.receiverPhone AS receiverPhone,
                   ctd.cargoTicketDetailId AS cargoTicketDetailId,
                   cargoType.cargoTypeName AS cargoTypeName,
                   cargoPrice.unit AS unit,
                   ctd.[description] AS detailDescription,
                   ctd.quantity AS quantity,
                   ctd.weightKg AS weightKg,
                   ctd.dimensionVol AS dimensionVol,
                   ctd.calculatedPrice AS calculatedPrice
            FROM cargo_ticket_detail ctd
            JOIN cargo_ticket ct ON ct.cargoTicketId = ctd.cargoTicketId
            JOIN customer customer ON customer.customerId = ct.customerId
            LEFT JOIN trip t ON t.tripId = ct.tripId
            LEFT JOIN route r ON r.routeId = t.routeId
            LEFT JOIN coach co ON co.coachId = t.coachId
            LEFT JOIN staff driver ON driver.staffId = t.driverId
                                  AND driver.staffPosition = 'DRIVER'
            JOIN staff seller ON seller.staffId = ct.soldBy
            LEFT JOIN ticket_agency ta ON ta.ticketAgencyId = seller.ticketAgencyId
            JOIN coach_stop pickup ON pickup.stopPointId = ct.pickupStopId
            JOIN coach_stop dropoff ON dropoff.stopPointId = ct.dropoffStopId
            JOIN cargo_type_price cargoPrice ON cargoPrice.cargoTypePriceId = ctd.cargoTypePriceId
            JOIN cargo_type cargoType ON cargoType.cargoTypeId = cargoPrice.cargoTypeId
            WHERE customer.accountId = :accountId
            ORDER BY ct.createdAt DESC, ct.cargoTicketId DESC, ctd.cargoTicketDetailId ASC
            """, nativeQuery = true)
    List<CargoOrderLookupProjection> findCargoOrdersByAccountId(@Param("accountId") Integer accountId);

    @Query(value = """
            SELECT DISTINCT ct.cargoTicketId AS cargoTicketId,
                   cs.stopPointId AS stopPointId,
                   cs.stopPointName AS stopPointName,
                   cs.[address] AS [address],
                   cs.city AS city,
                   rs.stopOrder AS stopOrder,
                   DATEADD(MINUTE, rs.minutesFromStart, t.departureTime) AS estimatedStopTime
            FROM cargo_ticket ct
            JOIN customer customer ON customer.customerId = ct.customerId
            JOIN trip t ON t.tripId = ct.tripId
            JOIN route_stop rs ON rs.routeId = t.routeId
            JOIN coach_stop cs ON cs.stopPointId = rs.stopPointId
            WHERE customer.accountId = :accountId
              AND rs.stopOrder BETWEEN
                  (SELECT pickupRs.stopOrder FROM route_stop pickupRs
                   WHERE pickupRs.routeId = t.routeId AND pickupRs.stopPointId = ct.pickupStopId)
                  AND
                  (SELECT dropoffRs.stopOrder FROM route_stop dropoffRs
                   WHERE dropoffRs.routeId = t.routeId AND dropoffRs.stopPointId = ct.dropoffStopId)
            ORDER BY ct.cargoTicketId, rs.stopOrder
            """, nativeQuery = true)
    List<CargoOrderStopProjection> findCargoOrderStopsByAccountId(@Param("accountId") Integer accountId);
}
