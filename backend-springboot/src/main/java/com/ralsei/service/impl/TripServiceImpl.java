package com.ralsei.service.impl;

import com.ralsei.dto.projection.coach.CoachLicensePlateProjection;
import com.ralsei.dto.projection.staff.StaffProjection;
import com.ralsei.dto.projection.trip.StaffTripInfoProjection;
import com.ralsei.dto.projection.trip.TripDetailProjection;
import com.ralsei.dto.projection.trip.TripFilterProjection;
import com.ralsei.dto.projection.trip.TripSummaryProjection;
import com.ralsei.dto.projection.trip.TripStopProjection;
import com.ralsei.dto.projection.trip.TripResourceProjection;
import com.ralsei.dto.request.trip.TripCreateRequest;
import com.ralsei.dto.request.trip.TripIncidentReplacementRequest;
import com.ralsei.dto.request.trip.TripUpdateRequest;
import com.ralsei.dto.response.PagedResponse;
import com.ralsei.dto.response.trip.ManagerTripIncidentResponse;
import com.ralsei.dto.response.CoachAndRouteStop.RouteDropdownDTO;
import com.ralsei.model.Trip;
import com.ralsei.model.enums.CoachStatus;
import com.ralsei.repository.RouteRepository;
import com.ralsei.repository.StaffRepository;
import com.ralsei.repository.TripRepository;
import com.ralsei.service.TripService;
import com.ralsei.util.FormatHandlerUtility;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Implements customer trip discovery and staff trip-management application
 * rules. Public filter validation is repeated here to protect non-HTTP callers
 * before repository parameters are constructed.
 */
@Service
@RequiredArgsConstructor
/**
 * Provides the trip service impl component for the application.
 */
public class TripServiceImpl implements TripService {
    private static final Logger LOGGER = LoggerFactory.getLogger(TripService.class);
    private static final ZoneId BUSINESS_TIME_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Set<String> WRITABLE_TRIP_STATUSES = Set.of("SCHEDULED", "IN_PROGRESS", "COMPLETED");
    private final TripRepository tripRepository;
    private final RouteRepository routeRepository;
    private final StaffRepository staffRepository;

    /**
     * Returns stops for the route assigned to the requested trip, avoiding the
     * ambiguity of looking up a coach by date.
     */
    @Override
    @Transactional(readOnly = true)
    /**
     * Returns the trip stops.
     *
     * @param tripId the value supplied for this operation
     *
     * @return the trip stops
     */
    public List<TripStopProjection> getTripStops(Integer tripId) {
        if (tripId == null || tripId < 1) {
            throw new IllegalArgumentException("Trip id must be greater than zero.");
        }
        return tripRepository.findTripStopsByTripId(tripId);
    }
    /**
     * Inserts a new trip into the database after validating the departure time
     * and resource availability.
     *
     * @param tripRequest the request containing trip details
     * @return a message indicating the result of the operation
     */
    @Override
    /**
     * Executes the insert trip operation.
     *
     * @param tripRequest the value supplied for this operation
     *
     * @return the operation result
     */
    public String insertTrip(TripCreateRequest tripRequest) {
        String prompt = "";
        if (tripRequest.getDepartureTime() == null
                || tripRequest.getDepartureTime().isBefore(currentMinute())) {
            LOGGER.warn("Validation failed: departure time is missing or in the past.");
            prompt = "Không thể tạo chuyến xe trong quá khứ, vui lòng tạo lại.";
        } else if (!resourcesAreAvailable(tripRequest.getRouteId(), tripRequest.getCoachId(),
                tripRequest.getDriverId(), tripRequest.getAttendantId(), tripRequest.getDepartureTime(), null)) {
            prompt = "Xe hoặc nhân sự đã có lịch trùng. Vui lòng chọn lại.";
        } else {
            try {
                tripRepository.insertTrip(tripRequest.getRouteId(), tripRequest.getCoachId(),
                        tripRequest.getDepartureTime(), tripRequest.getStatus(), tripRequest.getDriverId(),
                        tripRequest.getAttendantId());

                LOGGER.info("Tạo chuyến xe mới thành công. ID: ");
                prompt = "Tạo chuyến xe mới thành công";
            } catch (Exception e) {
                LOGGER.error("Could not create trip. Reason: {}", e.getMessage());
                prompt = "Lỗi hệ thống, tạo chuyến thất bại!";
            }
        }
        return prompt;
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<TripDetailProjection> getTripDetails(
            LocalDateTime start,
            LocalDateTime end,
            String route,
            int page,
            int size) {
        route = FormatHandlerUtility.formatProvinceName(route);
        if (start != null && end != null && start.isAfter(end)) {
            throw new IllegalArgumentException("Your mom are in the past, please check your date range!");
        }

        Pageable pageable = PageRequest.of(page, size);
        LocalDateTime currentTime = LocalDateTime.now();
        Page<TripDetailProjection> tripPage = tripRepository.findTripDetails(
                start, end, currentTime, route, pageable);

        return new PagedResponse<>(
                tripPage.getContent(),
                tripPage.getNumber(),
                tripPage.getSize(),
                tripPage.getTotalElements(),
                tripPage.getTotalPages(),
                tripPage.isLast());
    }

    /**
     * Applies validated public filters and converts time ranges into the fixed
     * minute-of-day parameters used by the customer repository query.
     *
     * <p>These guards deliberately duplicate transport validation. Services are
     * also called from tests and other Java code where MVC's {@code @Valid} is
     * not present, and unsafe values must never reach SQL through those paths.</p>
     */
    @Override
    @Transactional(readOnly = true)
    public PagedResponse<TripFilterProjection> getFilteredTripDetails(
            LocalDateTime start,
            LocalDateTime end,
            String route,
            List<String> timeSlots,
            List<String> layouts,
            Double minPrice,
            Double maxPrice,
            int page,
            int size) {

        List<String> normalizedTimeSlots = normalizeCustomerTimeSlots(timeSlots);
        List<String> normalizedLayoutKeywords = normalizeCustomerCoachTypeFilters(layouts);
        validateCustomerTripFilters(normalizedTimeSlots, normalizedLayoutKeywords, minPrice, maxPrice);
        int checkTimeSlots = normalizedTimeSlots.isEmpty() ? 0 : 1;

        Integer slot1StartMinute = null, slot1EndMinute = null;
        Integer slot2StartMinute = null, slot2EndMinute = null;
        Integer slot3StartMinute = null, slot3EndMinute = null;
        Integer slot4StartMinute = null, slot4EndMinute = null;

        if (checkTimeSlots == 1) {
            for (int i = 0; i < normalizedTimeSlots.size(); i++) {
                String slot = normalizedTimeSlots.get(i);
                if (slot != null && slot.contains("-")) {
                    String[] parts = slot.split("-");
                    Integer startMinute = parseTimeToMinuteOfDay(parts[0].trim());
                    Integer endMinute = parseTimeToMinuteOfDay(parts[1].trim());
                    switch (i) {
                        case 0 -> {
                            slot1StartMinute = startMinute;
                            slot1EndMinute = endMinute;
                        }
                        case 1 -> {
                            slot2StartMinute = startMinute;
                            slot2EndMinute = endMinute;
                        }
                        case 2 -> {
                            slot3StartMinute = startMinute;
                            slot3EndMinute = endMinute;
                        }
                        case 3 -> {
                            slot4StartMinute = startMinute;
                            slot4EndMinute = endMinute;
                        }
                    }
                }
            }
        }

        int checkLayouts = normalizedLayoutKeywords.isEmpty() ? 0 : 1;
        String layoutKeyword1 = normalizedLayoutKeywords.size() > 0 ? normalizedLayoutKeywords.get(0) : null;
        String layoutKeyword2 = normalizedLayoutKeywords.size() > 1 ? normalizedLayoutKeywords.get(1) : null;
        String layoutKeyword3 = normalizedLayoutKeywords.size() > 2 ? normalizedLayoutKeywords.get(2) : null;

        // 3. Khởi tạo phân trang
        Pageable pageable = PageRequest.of(page, size);
        LocalDateTime currentTime = LocalDateTime.now();
        // 4. Gọi repository
        Page<TripFilterProjection> filteredPage = tripRepository.filterTrips(
                start, end, currentTime, route,
                checkTimeSlots,
                slot1StartMinute, slot1EndMinute,
                slot2StartMinute, slot2EndMinute,
                slot3StartMinute, slot3EndMinute,
                slot4StartMinute, slot4EndMinute,
                checkLayouts,
                layoutKeyword1,
                layoutKeyword2,
                layoutKeyword3,
                minPrice, maxPrice,
                pageable);

        // 5. Đóng gói response
        return new PagedResponse<>(
                filteredPage.getContent(),
                filteredPage.getNumber(),
                filteredPage.getSize(),
                filteredPage.getTotalElements(),
                filteredPage.getTotalPages(),
                filteredPage.isLast());
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<TripSummaryProjection> getAllTripSummaries(
            LocalDate date, Integer routeId, String period, String status, int page, int size) {
        LocalDate targetDate = (date != null) ? date : LocalDate.now(BUSINESS_TIME_ZONE);
        String departureDateStr = targetDate.toString();
        Pageable pageable = PageRequest.of(page, size);
        String normalizedPeriod = (period == null || period.isBlank()) ? null : period.toUpperCase();
        String normalizedStatus = (status == null || status.isBlank()) ? null : status.toUpperCase();
        Page<TripSummaryProjection> summaryPage = tripRepository.viewAllTripSummaries(
                departureDateStr, routeId, normalizedPeriod, normalizedStatus, pageable);

        return new PagedResponse<>(
                summaryPage.getContent(),
                summaryPage.getNumber(),
                summaryPage.getSize(),
                summaryPage.getTotalElements(),
                summaryPage.getTotalPages(),
                summaryPage.isLast());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ManagerTripIncidentResponse> getManagerTripIncidents(LocalDate date) {
        LocalDate targetDate = date != null ? date : LocalDate.now(BUSINESS_TIME_ZONE);
        return tripRepository.findIncidentTrips(
                        CoachStatus.HAVE_INCIDENT,
                        targetDate.atStartOfDay(),
                        targetDate.plusDays(1).atStartOfDay())
                .stream()
                .map(trip -> new ManagerTripIncidentResponse(
                        trip.getTripId(),
                        trip.getRoute().getRouteName(),
                        trip.getCoach().getLicensePlate(),
                        trip.getDepartureTime()))
                .toList();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Price ranges are checkbox-friendly constants from the staff UI. Unknown
     * values are ignored so one stale browser tab cannot poison the whole
     * request. Status values are normalized to uppercase because the database
     * stores trip states as uppercase strings.</p>
     */
    @Override
    @Transactional(readOnly = true)
    public PagedResponse<StaffTripInfoProjection> getStaffTripInfos(
            LocalDate date,
            String city,
            String timeFrom,
            String timeTo,
            String coachTypeKeyword,
            List<String> priceRanges,
            List<String> statuses,
            String driverName,
            int page,
            int size) {
        LocalDate today = LocalDate.now(BUSINESS_TIME_ZONE);
        LocalDate departureDate = (date != null) ? date : today;
        if (departureDate.isBefore(today)) {
            throw new IllegalArgumentException("Không thể tra cứu chuyến xe trong quá khứ.");
        }
        LocalDate nextDepartureDate = departureDate.plusDays(1);

        Integer timeFromMinute = parseOptionalTimeToMinute(timeFrom);
        Integer timeToMinute = parseOptionalTimeToMinute(timeTo);
        if (timeFromMinute != null && timeToMinute != null && timeFromMinute > timeToMinute) {
            throw new IllegalArgumentException("Giờ bắt đầu phải nhỏ hơn hoặc bằng giờ kết thúc.");
        }

        List<String> normalizedStatuses = normalizeUppercaseFilters(statuses);
        int checkStatuses = normalizedStatuses.isEmpty() ? 0 : 1;
        if (normalizedStatuses.isEmpty()) {
            normalizedStatuses = List.of("__NO_STATUS__");
        }

        List<String> normalizedPriceRanges = normalizeUppercaseFilters(priceRanges);
        int priceLow = normalizedPriceRanges.contains("LOW") ? 1 : 0;
        int priceMiddle = normalizedPriceRanges.contains("MIDDLE") ? 1 : 0;
        int priceHigh = normalizedPriceRanges.contains("HIGH") ? 1 : 0;
        int checkPrices = (priceLow == 1 || priceMiddle == 1 || priceHigh == 1) ? 1 : 0;

        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1));
        Page<StaffTripInfoProjection> tripPage = tripRepository.findStaffTripInfos(
                departureDate,
                nextDepartureDate,
                blankToNull(city),
                timeFromMinute,
                timeToMinute,
                resolveCoachTypeKeyword(coachTypeKeyword),
                checkPrices,
                priceLow,
                priceMiddle,
                priceHigh,
                checkStatuses,
                normalizedStatuses,
                blankToNull(driverName),
                pageable);

        return new PagedResponse<>(
                tripPage.getContent(),
                tripPage.getNumber(),
                tripPage.getSize(),
                tripPage.getTotalElements(),
                tripPage.getTotalPages(),
                tripPage.isLast());
    }

    @Scheduled(cron = "0 0 2 * * ?")
    /**
     * Executes the execute auto generate schedule operation.
     */
    public void executeAutoGenerateSchedule() {
        try {
            int countDate = tripRepository.countDistinctDaysWithSchedule(null, null);
            LocalDate targetStartDate = LocalDate.now();
            if (countDate > 0 && countDate <= 3) {
                targetStartDate.plusDays(4);
                tripRepository.autoGenerateWeeklySchedule(targetStartDate.toString());
            }
            System.out.println("World Machine: Đã tự động sinh gối đầu lịch tuần mới cho ngày " + targetStartDate);
        } catch (Exception e) {
            System.out.println("World Machine ERROR: Lỗi cỗ máy sinh lịch tự động: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    /**
     * Updates the trip.
     *
     * @param tripId the value supplied for this operation
     * @param updateRequest the value supplied for this operation
     *
     * @return the updated trip
     */
    public String updateTrip(Integer tripId, TripUpdateRequest updateRequest) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy chuyến xe có ID: " + tripId));
        if (!"SCHEDULED".equals(trip.getStatus())) {
            LOGGER.warn("Rejected update for non-scheduled trip: tripId={}, status={}", tripId, trip.getStatus());
            return "Chuyến xe đã bắt đầu hoặc kết thúc, chỉ có thể xem thông tin.";
        }
        LocalDateTime validationTime = currentMinute();
        if (updateRequest.departureTime() == null
                || updateRequest.departureTime().isBefore(validationTime)) {
            LOGGER.warn("Rejected past trip update: tripId={}, requestedDeparture={}",
                    tripId, updateRequest.departureTime());
            return "Không thể chuyển chuyến xe về thời gian trong quá khứ. Thời gian nhận được: "
                    + updateRequest.departureTime() + ", thời gian hệ thống: " + validationTime + ".";
        }
        if (!WRITABLE_TRIP_STATUSES.contains(updateRequest.status())) {
            return "Trạng thái chuyến xe không hợp lệ.";
        }
        if (!resourcesAreAvailable(updateRequest.routeId(), updateRequest.coachId(),
                updateRequest.driverId(), updateRequest.attendantId(), updateRequest.departureTime(), tripId)) {
            return "Xe hoặc nhân sự đã có lịch trùng. Vui lòng chọn lại.";
        }
        int updated = tripRepository.updateScheduledTrip(tripId, updateRequest.routeId(), updateRequest.coachId(),
                updateRequest.departureTime(), updateRequest.status(), updateRequest.driverId(),
                updateRequest.attendantId());
        if (updated != 1) {
            return "Chuyến xe đã thay đổi trạng thái, vui lòng tải lại danh sách.";
        }
        LOGGER.info("Cập nhật chuyến xe thành công. ID: {}", tripId);
        return "Cập nhật thông tin chuyến xe thành công";
    }

    @Override
    @Transactional
    /**
     * Deletes the trip.
     *
     * @param tripId the value supplied for this operation
     */
    public String deleteTrip(Integer tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy chuyến xe có ID: " + tripId));
        if (!"SCHEDULED".equals(trip.getStatus())) {
            return "Chuyến xe đã bắt đầu hoặc kết thúc, chỉ có thể xem thông tin.";
        }
        int cancelled = tripRepository.cancelScheduledTrip(tripId);
        if (cancelled != 1) {
            return "Chuyến xe đã thay đổi trạng thái, vui lòng tải lại danh sách.";
        }
        LOGGER.info("Hủy chuyến xe thành công. ID: {}", tripId);
        return "Xóa chuyến xe thành công!";
    }

    /** Accepts both historical cancellation spellings stored by older code. */
    private boolean isCancelled(String status) {
        return "CANCELED".equals(status) || "CANCELLED".equals(status);
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
    //TODO: sửa lại logic thằng của nợ này. 
    @Override
    /**
     * Returns the staff name drop down.
     *
     * @param date the value supplied for this operation
     *
     * @return the staff name drop down
     */
    public List<StaffProjection> getStaffNameDropDown(LocalDate date) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getStaffNameDropDown'");
    }

    @Override
    /**
     * Returns the coach info drop down.
     *
     * @param date the value supplied for this operation
     *
     * @return the coach info drop down
     */
    public List<CoachLicensePlateProjection> getCoachInfoDropDown(LocalDate date) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getCoachInfoDropDown'");
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<TripResourceProjection> getAvailableCoaches(
            Integer routeId, LocalDateTime departureTime, Integer excludeTripId) {
        validateResourceRequest(routeId, departureTime);
        return tripRepository.findAvailableCoaches(routeId, departureTime, excludeTripId);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    /**
     * Returns the available drivers.
     *
     * @param departureTime the value supplied for this operation
     * @param excludeTripId the value supplied for this operation
     *
     * @return the available drivers
     */
    public List<TripResourceProjection> getAvailableDrivers(LocalDateTime departureTime, Integer excludeTripId) {
        validateResourceRequest(1, departureTime);
        return tripRepository.findAvailableStaff("DRIVER", departureTime, excludeTripId);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    /**
     * Returns the available attendants.
     *
     * @param departureTime the value supplied for this operation
     * @param excludeTripId the value supplied for this operation
     *
     * @return the available attendants
     */
    public List<TripResourceProjection> getAvailableAttendants(LocalDateTime departureTime, Integer excludeTripId) {
        validateResourceRequest(1, departureTime);
        return tripRepository.findAvailableStaff("ATTENDANT", departureTime, excludeTripId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TripResourceProjection> getIncidentReplacementCoaches(Integer tripId, Integer routeId) {
        requireUnresolvedIncidentTrip(tripId);
        validateReplacementRoute(tripId, routeId);
        int requiredSeats = tripRepository.countIncidentManifestPassengers(tripId);
        return tripRepository.findIncidentReplacementCoaches(currentBusinessTime(), tripId, requiredSeats);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TripResourceProjection> getIncidentReplacementDrivers(Integer tripId) {
        requireUnresolvedIncidentTrip(tripId);
        return tripRepository.findAvailableStaff("DRIVER", currentBusinessTime(), tripId);
    }

    @Override
    @Transactional
    public String replaceIncidentTrip(Integer tripId, TripIncidentReplacementRequest request) {
        requireUnresolvedIncidentTrip(tripId);
        validateReplacementRoute(tripId, request.routeId());

        LocalDateTime dispatchTime = currentBusinessTime();
        int requiredSeats = tripRepository.countIncidentManifestPassengers(tripId);
        boolean coachAvailable = tripRepository
                .findIncidentReplacementCoaches(dispatchTime, tripId, requiredSeats)
                .stream()
                .anyMatch(option -> request.coachId().equals(option.getId()));
        boolean driverAvailable = tripRepository
                .findAvailableStaff("DRIVER", dispatchTime, tripId)
                .stream()
                .anyMatch(option -> request.driverId().equals(option.getId()));

        if (!coachAvailable || !driverAvailable) {
            throw new IllegalArgumentException(
                    "Xe khách hoặc tài xế vừa được phân công cho chuyến khác. Vui lòng chọn lại.");
        }

        int updated = tripRepository.dispatchIncidentReplacement(
                tripId, request.routeId(), request.coachId(), request.driverId(), dispatchTime);
        if (updated != 1) {
            throw new IllegalArgumentException(
                    "Sự cố đã được xử lý ở một phiên khác. Vui lòng tải lại danh sách chuyến.");
        }

        LOGGER.warn("Dispatched incident replacement: tripId={}, routeId={}, coachId={}, driverId={}, departureTime={}",
                tripId, request.routeId(), request.coachId(), request.driverId(), dispatchTime);
        return "Đã điều xe thay thế và tiếp tục chuyến ngay lúc "
                + dispatchTime.toLocalTime() + ". Toàn bộ hành khách và hàng hóa vẫn thuộc chuyến này.";
    }

    /** Loads only an in-progress trip whose currently assigned coach is incident-locked. */
    private Trip requireUnresolvedIncidentTrip(Integer tripId) {
        if (tripId == null || tripId < 1) {
            throw new IllegalArgumentException("Mã chuyến gặp sự cố không hợp lệ.");
        }
        Trip trip = tripRepository.findByIdWithRouteAndCoach(tripId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy chuyến xe #" + tripId + "."));
        if (!"IN_PROGRESS".equals(trip.getStatus())
                || trip.getCoach().getStatus() != CoachStatus.HAVE_INCIDENT) {
            throw new IllegalArgumentException("Chuyến xe không còn sự cố chưa xử lý.");
        }
        return trip;
    }

    /** Protects existing passenger and cargo legs when the manager selects a route. */
    private void validateReplacementRoute(Integer tripId, Integer routeId) {
        if (routeId == null || routeRepository.findByRouteIdAndIsActiveTrue(routeId).isEmpty()) {
            throw new IllegalArgumentException("Tuyến đường thay thế không tồn tại hoặc đã ngừng hoạt động.");
        }
        if (tripRepository.countManifestItemsOutsideRoute(tripId, routeId) > 0) {
            throw new IllegalArgumentException(
                    "Tuyến đường này không đi qua đầy đủ điểm đón/trả của hành khách hoặc hàng hóa hiện tại.");
        }
    }

    /** Ensures a resource lookup has enough data and never targets a past trip. */
    private void validateResourceRequest(Integer routeId, LocalDateTime departureTime) {
        if (routeId == null || departureTime == null
                || departureTime.isBefore(currentMinute())) {
            throw new IllegalArgumentException("Tuyến đường và thời gian khởi hành tương lai là bắt buộc.");
        }
    }

    /**
     * Matches the minute precision exposed by the staff time picker. Comparing
     * hidden seconds made the current selectable minute incorrectly look past.
     */
    private LocalDateTime currentMinute() {
        return LocalDateTime.now(BUSINESS_TIME_ZONE).truncatedTo(ChronoUnit.MINUTES);
    }

    /** Incident dispatch records the actual save instant instead of the old schedule minute. */
    private LocalDateTime currentBusinessTime() {
        return LocalDateTime.now(BUSINESS_TIME_ZONE).withNano(0);
    }

    /** Converts an optional HH:mm filter into minute-of-day for SQL comparison. */
    private Integer parseOptionalTimeToMinute(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        LocalTime time = LocalTime.parse(value.trim());
        return time.getHour() * 60 + time.getMinute();
    }

    /** Normalizes repeated checkbox/filter params while dropping empty values. */
    private List<String> normalizeUppercaseFilters(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toUpperCase())
                .distinct()
                .toList();
    }

    /** Returns null for blank request strings so repository SQL stays simple. */
    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    /**
     * Converts UI category values into safe LIKE patterns for coach type names.
     * Unknown values are ignored instead of being passed directly into SQL.
     */
    private String resolveCoachTypeKeyword(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return switch (value.trim().toUpperCase()) {
            case "LIMOUSINE" -> "%limousine%";
            case "LUXURY" -> "%luxury%";
            case "TRUYEN_THONG" -> "%truyền thống%";
            default -> null;
        };
    }

    /** Verifies all selected resources still remain free at write time. */
    private boolean resourcesAreAvailable(Integer routeId, Integer coachId, Integer driverId,
            Integer attendantId, LocalDateTime departureTime, Integer excludeTripId) {
        if (routeId == null || coachId == null || driverId == null || attendantId == null) {
            return false;
        }
        boolean coachFree = tripRepository.findAvailableCoaches(routeId, departureTime, excludeTripId)
                .stream().anyMatch(item -> coachId.equals(item.getId()));
        boolean driverFree = tripRepository.findAvailableStaff("DRIVER", departureTime, excludeTripId)
                .stream().anyMatch(item -> driverId.equals(item.getId()));
        boolean attendantFree = tripRepository.findAvailableStaff("ATTENDANT", departureTime, excludeTripId)
                .stream().anyMatch(item -> attendantId.equals(item.getId()));
        return coachFree && driverFree && attendantFree && !driverId.equals(attendantId);
    }

    /**
     * Converts customer filter slot keys from the React UI into SQL-friendly time
     * ranges while still accepting direct "HH:mm-HH:mm" values from future clients.
     *
     * @param timeSlots raw request values bound by Spring from query parameters
     * @return normalized time ranges in "HH:mm-HH:mm" format
     */
    private List<String> normalizeCustomerTimeSlots(List<String> timeSlots) {
        if (timeSlots == null || timeSlots.isEmpty()) {
            return List.of();
        }

        List<String> normalizedSlots = new ArrayList<>();
        for (String rawSlot : timeSlots) {
            if (rawSlot == null || rawSlot.isBlank()) {
                continue;
            }

            String[] requestedSlots = rawSlot.split(",");
            for (String requestedSlot : requestedSlots) {
                String slot = requestedSlot.trim();
                if (slot.isEmpty()) {
                    continue;
                }

                switch (slot) {
                    case "EARLY_MORNING" -> normalizedSlots.add("00:00-06:00");
                    case "MORNING" -> normalizedSlots.add("06:00-12:00");
                    case "AFTERNOON" -> normalizedSlots.add("12:00-18:00");
                    case "EVENING" -> normalizedSlots.add("18:00-23:59");
                    default -> {
                        if (slot.contains("-")) {
                            normalizedSlots.add(slot);
                        }
                    }
                }
            }
        }
        return normalizedSlots;
    }

    /**
     * Parses an "HH:mm" or "HH:mm:ss" value into minutes after midnight so SQL
     * Server can compare departure times numerically instead of comparing TIME
     * to string parameters.
     *
     * @param timeValue time value from the normalized customer filter
     * @return minute of day, or {@code null} when the input is malformed
     */
    private Integer parseTimeToMinuteOfDay(String timeValue) {
        if (timeValue == null || timeValue.isBlank()) {
            return null;
        }

        String[] parts = timeValue.split(":");
        if (parts.length < 2) {
            return null;
        }

        try {
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
                return null;
            }
            return hour * 60 + minute;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * Converts public-site coach filter values into SQL LIKE patterns. The
     * frontend sends simple keywords: "Limousine", "luxury", or "truyền thống".
     *
     * @param layouts raw request values from the customer filter
     * @return SQL LIKE patterns for coach type filtering
     */
    private List<String> normalizeCustomerCoachTypeFilters(List<String> layouts) {
        if (layouts == null || layouts.isEmpty()) {
            return List.of();
        }

        List<String> keywords = new ArrayList<>();
        for (String rawLayout : layouts) {
            if (rawLayout == null || rawLayout.isBlank()) {
                continue;
            }

            String[] requestedLayouts = rawLayout.split(",");
            for (String requestedLayout : requestedLayouts) {
                String layout = requestedLayout.trim();
                if (layout.isEmpty()) {
                    continue;
                }

                switch (layout) {
                    case "COACH_STANDARD", "Xe Khách Truyền Thống 38 chỗ" ->
                        addUniqueKeyword(keywords, "%truyền thống%");
                    case "COACH_LIMOUSINE", "Xe Limousine VIP 20 phòng" ->
                        addUniqueKeyword(keywords, "%Limousine%");
                    case "COACH_LUXURY", "Xe Giường Nằm Luxury 32 chỗ" ->
                        addUniqueKeyword(keywords, "%luxury%");
                    default -> addUniqueKeyword(keywords, "%" + layout + "%");
                }
            }
        }
        return keywords;
    }

    /**
     * Enforces repository parameter limits and numeric ordering for callers
     * that invoke the service without passing through Spring MVC validation.
     */
    private void validateCustomerTripFilters(
            List<String> timeSlots,
            List<String> layoutKeywords,
            Double minPrice,
            Double maxPrice) {
        if (timeSlots.size() > 4) {
            throw new IllegalArgumentException("At most four time slots may be requested.");
        }
        for (String slot : timeSlots) {
            String[] bounds = slot.split("-", 2);
            Integer startMinute = bounds.length == 2 ? parseTimeToMinuteOfDay(bounds[0].trim()) : null;
            Integer endMinute = bounds.length == 2 ? parseTimeToMinuteOfDay(bounds[1].trim()) : null;
            if (startMinute == null || endMinute == null || startMinute > endMinute) {
                throw new IllegalArgumentException("Time slots must use a valid forward HH:mm-HH:mm range.");
            }
        }
        if (layoutKeywords.size() > 3) {
            throw new IllegalArgumentException("At most three layouts may be requested.");
        }
        if ((minPrice != null && (!Double.isFinite(minPrice) || minPrice < 0))
                || (maxPrice != null && (!Double.isFinite(maxPrice) || maxPrice < 0))) {
            throw new IllegalArgumentException("Price bounds must be finite and non-negative.");
        }
        if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
            throw new IllegalArgumentException("Minimum price must not exceed maximum price.");
        }
    }

    /**
     * Adds a SQL LIKE pattern once so combined filters do not duplicate
     * repository parameters.
     *
     * @param keywords accumulated SQL LIKE patterns
     * @param keyword candidate SQL LIKE pattern
     */
    private void addUniqueKeyword(List<String> keywords, String keyword) {
        if (!keywords.contains(keyword)) {
            keywords.add(keyword);
        }
    }


}
