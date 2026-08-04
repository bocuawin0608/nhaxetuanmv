package com.ralsei.service.impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ralsei.dto.request.cargoticket.CargoTicketRequest;
import com.ralsei.dto.request.cargoticket.CargoTicketWithDetailsRequest;
import com.ralsei.dto.request.cargoticket.CargoTripAssignRequest;
import com.ralsei.dto.request.cargoticket.ConfirmReceivedRequest;
import com.ralsei.dto.request.cargoticket.ReceiverPaymentMethodRequest;
import com.ralsei.dto.response.PagedResponse;
import com.ralsei.dto.response.cargoticket.CargoAssignableBoardResponse;
import com.ralsei.dto.response.cargoticket.CargoAssignableTicketResponse;
import com.ralsei.dto.response.cargoticket.CargoTicketResponse;
import com.ralsei.dto.response.cargoticket.CargoOperationalTripResponse;
import com.ralsei.dto.response.cargoticket.CargoOperationalTripPageResponse;
import com.ralsei.dto.response.cargoticket.CargoReceivingTripPageResponse;
import com.ralsei.dto.response.cargoticket.CargoReceivingTripResponse;
import com.ralsei.dto.response.cargoticket.CargoTicketFormOptionsResponse;
import com.ralsei.dto.response.cargoticket.CargoTripAssignResponse;
import com.ralsei.dto.response.cargoticket.CustomerContactResponse;
import com.ralsei.dto.response.cargoticketdetail.CargoTicketDetailResponse;
import com.ralsei.exception.BusinessRuleException;
import com.ralsei.exception.ResourceNotFoundException;
import com.ralsei.model.CargoTicket;
import com.ralsei.model.CargoTicketDetail;
import com.ralsei.model.Payment;
import com.ralsei.repository.CargoTicketRepository;
import com.ralsei.repository.CargoTicketDetailRepository;
import com.ralsei.repository.CoachStopRepository;
import com.ralsei.repository.CustomerRepository;
import com.ralsei.repository.PaymentRepository;
import com.ralsei.repository.RouteRepository;
import com.ralsei.repository.RouteStopRepository;
import com.ralsei.repository.StaffRepository;
import com.ralsei.repository.TripRepository;
import com.ralsei.repository.TicketAgencyRepository;
import com.ralsei.service.CargoTicketService;
import com.ralsei.service.TransactionIdGenerator;
import com.ralsei.model.Staff;
import com.ralsei.dto.request.cargoticket.TripByStopRequest;
import com.ralsei.dto.request.cargoticketdetail.CargoTicketDetailRequest;
import com.ralsei.dto.response.cargoticket.TripByStopResponse;
import com.ralsei.dto.response.cargoticketdetail.CargoTicketDetailPriceResponse;
import com.ralsei.dto.request.cargoticketdetail.CargoTicketDetailPriceRequest;
import com.ralsei.repository.CargoTypePriceRepository;
import com.ralsei.model.CargoTypePrice;
import com.ralsei.model.TicketAgency;
import com.ralsei.util.FreightCalculatorUtility;
import com.ralsei.util.CargoVolumePolicy;
import com.ralsei.service.cargoticket.CargoTicketPaymentPolicy;

import lombok.RequiredArgsConstructor;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
/**
 * Provides the cargo ticket service impl component for the application.
 */
public class CargoTicketServiceImpl implements CargoTicketService {
    private static final String STATUS_ABANDONED = "ABANDONED";
    private static final BigDecimal CARGO_CAPACITY_M3 = CargoOperationalTripResponse.capacity();

    private final CargoTicketRepository cargoTicketRepository;
    private final CargoTicketDetailRepository cargoTicketDetailRepository;
    private final TripRepository tripRepository;
    private final CustomerRepository customerRepository;
    private final CoachStopRepository coachStopRepository;
    private final StaffRepository staffRepository;
    private final PaymentRepository paymentRepository;
    private final CargoTypePriceRepository cargoTypePriceRepository;
    private final TransactionIdGenerator transactionIdGenerator;
    private final RouteRepository routeRepository;
    private final RouteStopRepository routeStopRepository;
    private final TicketAgencyRepository ticketAgencyRepository;
    private final CargoTicketPaymentPolicy cargoTicketPaymentPolicy;

    @Value("${sepay.bank.account}")
    private String sepayBankAccount;

    @Value("${sepay.bank.name}")
    private String sepayBankName;

    @Override
    /**
     * Returns the all cargo tickets.
     *
     * @param page the value supplied for this operation
     * @param size the value supplied for this operation
     *
     * @return the all cargo tickets
     */
    public PagedResponse<CargoTicketResponse> getAllCargoTickets(
            String status, Integer tripId, Integer accountId, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("cargoTicketId").descending());
        Staff currentStaff = requireAgencyStaff(accountId);
        Page<CargoTicket> result;
        if (status == null || status.isBlank()) {
            result = cargoTicketRepository.findAllForAgency(currentStaff.getTicketAgencyId(), pageable);
        } else {
            result = cargoTicketRepository.findStaffQueueByStatusAndAgency(
                    status.trim().toUpperCase(), currentStaff.getTicketAgencyId(), tripId, pageable);
        }
        return new PagedResponse<>(
                result.map(this::mapToResponse).getContent(),
                result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages(), result.isLast());
    }

    @Override
    public CargoOperationalTripPageResponse getUpcomingOperationalTrips(
            Integer accountId, int page, int size) {
        Staff currentStaff = requireAgencyStaff(accountId);
        TicketAgency agency = ticketAgencyRepository
                .findByTicketAgencyIdAndIsActiveTrue(currentStaff.getTicketAgencyId())
                .orElseThrow(() -> new BusinessRuleException("Văn phòng vé của nhân viên không hoạt động."));
        var agencyStop = coachStopRepository.findById(agency.getStopPointId())
                .orElseThrow(() -> new BusinessRuleException("Văn phòng vé chưa được gán cho điểm dừng hợp lệ."));
        BigDecimal capacity = CargoOperationalTripResponse.capacity();
        Page<CargoOperationalTripResponse> tripPage = tripRepository
                .findUpcomingCargoOperationalTrips(accountId, PageRequest.of(page, size))
                .map(trip -> {
                    BigDecimal used = trip.getUsedCargoVolume() == null
                            ? BigDecimal.ZERO
                            : trip.getUsedCargoVolume();
                    return CargoOperationalTripResponse.builder()
                            .tripId(trip.getTripId())
                            .routeId(trip.getRouteId())
                            .routeName(trip.getRouteName())
                            .departureTime(trip.getDepartureTime())
                            .pickupTime(trip.getPickupTime())
                            .pickupStopId(trip.getPickupStopId())
                            .pickupStopName(trip.getPickupStopName())
                            .pickupCity(trip.getPickupCity())
                            .tripStatus(trip.getTripStatus())
                            .licensePlate(trip.getLicensePlate())
                            .coachTypeName(trip.getCoachTypeName())
                            .driverName(trip.getDriverName())
                            .driverPhone(trip.getDriverPhone())
                            .driverCccd(trip.getDriverCccd())
                            .attendantName(trip.getAttendantName())
                            .attendantPhone(trip.getAttendantPhone())
                            .attendantCccd(trip.getAttendantCccd())
                            .stopSummary(trip.getStopSummary())
                            .usedCargoVolume(used)
                            .cargoCapacity(capacity)
                            .full(used.compareTo(capacity) >= 0)
                            .build();
                });
        PagedResponse<CargoOperationalTripResponse> trips = new PagedResponse<>(
                tripPage.getContent(), tripPage.getNumber(), tripPage.getSize(),
                tripPage.getTotalElements(), tripPage.getTotalPages(), tripPage.isLast());
        return CargoOperationalTripPageResponse.builder()
                .ticketAgencyId(agency.getTicketAgencyId())
                .ticketAgencyName(agency.getTicketAgencyName())
                .stopPointId(agencyStop.getStopPointId())
                .stopPointName(agencyStop.getStopPointName())
                .city(agencyStop.getCity())
                .trips(trips)
                .build();
    }

    @Override
    /**
     * Returns coaches that have unloaded cargo awaiting acknowledgement at the
     * authenticated ticket staff member's destination office.
     *
     * @param accountId authenticated ticket-staff account
     * @param page      zero-based page number
     * @param size      requested page size
     * @return office context and a page of receiving coaches
     */
    public CargoReceivingTripPageResponse getReceivingTrips(
            Integer accountId, int page, int size) {
        Staff currentStaff = requireAgencyStaff(accountId);
        TicketAgency agency = ticketAgencyRepository
                .findByTicketAgencyIdAndIsActiveTrue(currentStaff.getTicketAgencyId())
                .orElseThrow(() -> new BusinessRuleException("Văn phòng vé của nhân viên không hoạt động."));
        var agencyStop = coachStopRepository.findById(agency.getStopPointId())
                .orElseThrow(() -> new BusinessRuleException("Văn phòng vé chưa được gán cho điểm dừng hợp lệ."));
        Page<CargoReceivingTripResponse> tripPage = tripRepository
                .findCargoReceivingTrips(agency.getTicketAgencyId(), PageRequest.of(page, size))
                .map(trip -> CargoReceivingTripResponse.builder()
                        .tripId(trip.getTripId())
                        .routeName(trip.getRouteName())
                        .departureTime(trip.getDepartureTime())
                        .tripStatus(trip.getTripStatus())
                        .licensePlate(trip.getLicensePlate())
                        .coachTypeName(trip.getCoachTypeName())
                        .driverName(trip.getDriverName())
                        .driverPhone(trip.getDriverPhone())
                        .driverCccd(trip.getDriverCccd())
                        .attendantName(trip.getAttendantName())
                        .attendantPhone(trip.getAttendantPhone())
                        .attendantCccd(trip.getAttendantCccd())
                        .lastCargoUpdateAt(trip.getLastCargoUpdateAt())
                        .waitingOrderCount(trip.getWaitingOrderCount())
                        .build());
        PagedResponse<CargoReceivingTripResponse> trips = new PagedResponse<>(
                tripPage.getContent(), tripPage.getNumber(), tripPage.getSize(),
                tripPage.getTotalElements(), tripPage.getTotalPages(), tripPage.isLast());
        return CargoReceivingTripPageResponse.builder()
                .ticketAgencyId(agency.getTicketAgencyId())
                .ticketAgencyName(agency.getTicketAgencyName())
                .stopPointId(agencyStop.getStopPointId())
                .stopPointName(agencyStop.getStopPointName())
                .city(agencyStop.getCity())
                .trips(trips)
                .build();
    }

    @Override
    /**
     * Returns the form options.
     *
     * @param pickupStopId  the value supplied for this operation
     * @param dropoffStopId the value supplied for this operation
     *
     * @return the form options
     */
    public CargoTicketFormOptionsResponse getFormOptions(
            Integer pickupStopId, Integer dropoffStopId, Integer accountId) {
        Staff currentStaff = requireAgencyStaff(accountId);
        TicketAgency agency = ticketAgencyRepository
                .findByTicketAgencyIdAndIsActiveTrue(currentStaff.getTicketAgencyId())
                .orElseThrow(() -> new BusinessRuleException("Văn phòng vé của nhân viên không hoạt động."));
        var agencyStop = coachStopRepository.findById(agency.getStopPointId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy điểm dừng của văn phòng vé."));
        Integer defaultRouteId = routeStopRepository
                .findDefaultCargoRouteIdForPickup(agency.getStopPointId());
        String defaultRouteName = null;
        if (defaultRouteId != null) {
            defaultRouteName = routeRepository.findById(defaultRouteId)
                    .map(route -> route.getRouteName())
                    .orElse(null);
        }

        return CargoTicketFormOptionsResponse.builder()
                .routes(routeRepository.findRoutesForDropdown())
                .trips(pickupStopId != null && dropoffStopId != null && !pickupStopId.equals(dropoffStopId)
                        ? tripRepository.findCargoTicketTripOptionsWithCoachType(pickupStopId, dropoffStopId)
                        : List.of())
                .customers(customerRepository.findCargoTicketCustomerOptions())
                .stops(coachStopRepository.findCargoTicketStopOptions())
                .sellers(staffRepository.findCargoTicketSellerOptionsWithUsername())
                .handlers(staffRepository.findCargoTicketHandlerOptions())
                .drivers(staffRepository.findCargoTicketDriverOptions())
                .agencyPickupStopId(agency.getStopPointId())
                .agencyPickupStopName(agencyStop.getStopPointName())
                .agencyCity(agencyStop.getCity())
                .defaultRouteId(defaultRouteId)
                .defaultRouteName(defaultRouteName)
                .build();
    }

    @Override
    /**
     * Returns the cargo ticket by id.
     *
     * @param id the value supplied for this operation
     *
     * @return the cargo ticket by id
     */
    public CargoTicketResponse getCargoTicketById(int id) {
        return mapToResponse(findByIdOrThrow(id));
    }

    @Override
    @Transactional
    /**
     * Creates the cargo ticket.
     *
     * @param request the value supplied for this operation
     *
     * @return the created cargo ticket
     */
    public CargoTicketResponse createCargoTicket(CargoTicketRequest request, Integer accountId) {
        Staff currentStaff = requireAgencyStaff(accountId);
        cargoTicketPaymentPolicy.requireSenderPaymentMethodOnCreate(
                request.getFeePayer(), request.getPaymentMethod());
        cargoTicketPaymentPolicy.rejectSenderBankCreateWithTrip(
                request.getTripId(), request.getFeePayer(), request.getPaymentMethod());
        requireTripOpenForCargo(request.getTripId());
        applyCreateBusinessDefaults(request, currentStaff);
        String ticketCode = generateTicketCode();
        validateReferences(request, currentStaff);

        CargoTicket ticket = new CargoTicket();
        copyRequest(request, ticket, ticketCode);
        // New orders always enter the editable waiting queue. Operational state
        // transitions belong to the loading/arrival workflows, not this form.
        ticket.setStatus("RECEIVED");
        CargoTicket savedTicket = cargoTicketRepository.save(ticket);

        // RECEIVER pays at destination — method is chosen there, not at create time.
        if (CargoTicketPaymentPolicy.FEE_SENDER.equalsIgnoreCase(savedTicket.getFeePayer())) {
            Payment payment = Payment.builder()
                    .cargoTicket(savedTicket)
                    .amount(savedTicket.getTotalPrice())
                    .paymentMethod(request.getPaymentMethod())
                    .transactionId(transactionIdGenerator.generateUniqueTransactionId())
                    .status(CargoTicketPaymentPolicy.STATUS_PENDING)
                    .refundAmount(BigDecimal.ZERO)
                    .build();
            paymentRepository.save(payment);
            cargoTicketPaymentPolicy.completeCashIfApplicableOnCreate(payment, savedTicket.getFeePayer());
        }

        return mapToResponse(savedTicket);
    }

    @Override
    @Transactional
    /**
     * Creates the cargo ticket with details.
     *
     * @param request the value supplied for this operation
     *
     * @return the created cargo ticket with details
     */
    public CargoTicketResponse createCargoTicketWithDetails(
            CargoTicketWithDetailsRequest request, Integer accountId) {
        requireAgencyStaff(accountId);
        cargoTicketPaymentPolicy.requireSenderPaymentMethodOnCreate(
                request.getFeePayer(), request.getPaymentMethod());
        cargoTicketPaymentPolicy.rejectSenderBankCreateWithTrip(
                request.getTripId(), request.getFeePayer(), request.getPaymentMethod());
        // Calculate the prices first to satisfy the Payment DB constraint (> 0)
        List<CargoTicketDetail> mappedDetails = new java.util.ArrayList<>();
        BigDecimal preCalculatedTotal = BigDecimal.ZERO;

        for (var d : request.getDetails()) {
            BigDecimal calcPrice = calculateDetailPrice(d.getCargoTypePriceId(), d.getDimensionVol(), d.getQuantity());
            preCalculatedTotal = preCalculatedTotal.add(calcPrice);

            mappedDetails.add(CargoTicketDetail.builder()
                    .cargoTypePriceId(d.getCargoTypePriceId())
                    .description(d.getDescription())
                    .quantity(d.getQuantity())
                    .weightKg(d.getWeightKg())
                    .dimensionVol(d.getDimensionVol())
                    .calculatedPrice(calcPrice)
                    .build());
        }

        BigDecimal requestedVolume = mappedDetails.stream()
                .map(this::occupiedVolume)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        CargoVolumePolicy.validateOrderVolume(requestedVolume);
        validateCapacity(request.getTripId(), requestedVolume, BigDecimal.ZERO);

        // Pass the pre-calculated total so the Payment row can be inserted successfully
        request.setTotalPrice(preCalculatedTotal);
        CargoTicketResponse response = createCargoTicket(request, accountId);

        CargoTicket ticket = cargoTicketRepository.findById(response.getCargoTicketId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn gửi hàng."));

        for (CargoTicketDetail detail : mappedDetails) {
            detail.setCargoTicket(ticket);
        }

        cargoTicketDetailRepository.saveAll(mappedDetails);

        // Ensure flushed to DB so updateTicketTotalPrice can read them
        cargoTicketDetailRepository.flush();
        updateTicketTotalPrice(ticket);

        return mapToResponse(ticket);
    }

    @Override
    /**
     * Returns the cargo ticket details by ticket id.
     *
     * @param cargoTicketId the value supplied for this operation
     *
     * @return the cargo ticket details by ticket id
     */
    public List<CargoTicketDetailResponse> getCargoTicketDetailsByTicketId(int cargoTicketId) {
        List<CargoTicketDetail> details = cargoTicketDetailRepository.findByCargoTicket_CargoTicketId(cargoTicketId);
        return details.stream().map(d -> CargoTicketDetailResponse.builder()
                .cargoTicketDetailId(d.getCargoTicketDetailId())
                .cargoTicketId(d.getCargoTicket().getCargoTicketId())
                .cargoTypePriceId(d.getCargoTypePriceId())
                .description(d.getDescription())
                .quantity(d.getQuantity())
                .weightKg(d.getWeightKg())
                .dimensionVol(d.getDimensionVol())
                .calculatedPrice(d.getCalculatedPrice())
                .build()).toList();
    }

    @Override
    @Transactional
    /**
     * Updates the cargo ticket.
     *
     * @param id      the value supplied for this operation
     * @param request the value supplied for this operation
     *
     * @return the updated cargo ticket
     */
    public CargoTicketResponse updateCargoTicket(int id, CargoTicketRequest request) {
        CargoTicket ticket = findByIdOrThrow(id);
        requirePending(ticket, "Chỉ đơn đang chờ mới được cập nhật.");
        Payment payment = cargoTicketPaymentPolicy.findPayment(ticket);
        // Header update does not recompute freight; amount is guarded only when details
        // change.
        guardPaidOrderFeeFields(ticket, payment, request);

        BigDecimal existingTotal = ticket.getTotalPrice();
        String ticketCode = ticket.getTicketCode();
        // Pickup stays at the origin office that created the order.
        request.setPickupStopId(ticket.getPickupStopId());
        requireTripOpenForCargo(request.getTripId());
        if (request.getTripId() != null
                && (ticket.getTripId() == null || !request.getTripId().equals(ticket.getTripId()))) {
            cargoTicketPaymentPolicy.requireReadyForTripAssignment(ticket);
        }
        validateReferences(request);

        copyRequest(request, ticket, ticketCode);
        // Price is derived exclusively from detail rows and cannot be edited by
        // a client payload. Status also remains in the pending queue.
        ticket.setTotalPrice(existingTotal);
        ticket.setStatus("RECEIVED");
        CargoTicket savedTicket = cargoTicketRepository.save(ticket);

        if (CargoTicketPaymentPolicy.FEE_SENDER.equalsIgnoreCase(savedTicket.getFeePayer())) {
            cargoTicketPaymentPolicy.requireSenderPaymentMethodOnCreate(
                    savedTicket.getFeePayer(), request.getPaymentMethod());
            if (payment != null && request.getPaymentMethod() != null
                    && !cargoTicketPaymentPolicy.isCompleted(payment)) {
                payment.setPaymentMethod(request.getPaymentMethod());
                paymentRepository.save(payment);
                cargoTicketPaymentPolicy.completeCashIfApplicableOnCreate(
                        payment, savedTicket.getFeePayer());
            } else if (payment == null && request.getPaymentMethod() != null) {
                payment = Payment.builder()
                        .cargoTicket(savedTicket)
                        .amount(savedTicket.getTotalPrice())
                        .paymentMethod(request.getPaymentMethod())
                        .transactionId(transactionIdGenerator.generateUniqueTransactionId())
                        .status(CargoTicketPaymentPolicy.STATUS_PENDING)
                        .refundAmount(BigDecimal.ZERO)
                        .build();
                paymentRepository.save(payment);
                cargoTicketPaymentPolicy.completeCashIfApplicableOnCreate(
                        payment, savedTicket.getFeePayer());
            }
        }

        return mapToResponse(savedTicket);
    }

    @Override
    @Transactional
    public CargoTicketResponse updateCargoTicketWithDetails(int id, CargoTicketWithDetailsRequest request) {
        CargoTicket ticket = findByIdOrThrow(id);
        requirePending(ticket, "Chỉ đơn đang chờ mới được cập nhật.");
        Payment existingPayment = cargoTicketPaymentPolicy.findPayment(ticket);
        List<CargoTicketDetail> existing = cargoTicketDetailRepository
                .findByCargoTicket_CargoTicketId(id);
        BigDecimal previousVolume = existing.stream().map(this::occupiedVolume)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal requestedVolume = request.getDetails().stream()
                .map(detail -> CargoVolumePolicy.occupiedVolume(
                        detail.getDimensionVol(), detail.getQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean freightInputsChanged = !sameFreightInputs(existing, request.getDetails());
        BigDecimal requestedTotal = request.getDetails().stream()
                .map(detail -> calculateDetailPrice(
                        detail.getCargoTypePriceId(), detail.getDimensionVol(), detail.getQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        guardPaidOrderFeeFields(ticket, existingPayment, request);
        // Paid orders may still change trip/contacts. Only reject when freight inputs
        // would change the collected amount (AF-2). Avoid false rejects from recalc
        // drift.
        if (freightInputsChanged) {
            guardPaidOrderAmount(existingPayment, requestedTotal);
        }

        CargoVolumePolicy.validateOrderVolume(requestedVolume);
        updateCargoTicket(id, request);
        validateCapacity(request.getTripId(), requestedVolume, previousVolume);

        var existingById = existing.stream().collect(java.util.stream.Collectors.toMap(
                CargoTicketDetail::getCargoTicketDetailId, detail -> detail));
        List<CargoTicketDetail> replacements = new java.util.ArrayList<>();
        for (CargoTicketDetailRequest detailRequest : request.getDetails()) {
            CargoTicketDetail detail;
            if (detailRequest.getCargoTicketDetailId() == null) {
                detail = new CargoTicketDetail();
                detail.setCargoTicket(ticket);
            } else {
                detail = existingById.remove(detailRequest.getCargoTicketDetailId());
                if (detail == null) {
                    throw new BusinessRuleException("Chi tiết hàng hóa không thuộc đơn đang cập nhật.");
                }
            }
            detail.setCargoTypePriceId(detailRequest.getCargoTypePriceId());
            detail.setDescription(detailRequest.getDescription());
            detail.setQuantity(detailRequest.getQuantity());
            detail.setWeightKg(detailRequest.getWeightKg());
            detail.setDimensionVol(detailRequest.getDimensionVol());
            if (cargoTicketPaymentPolicy.isCompleted(existingPayment) && !freightInputsChanged) {
                // Keep the amount that was already collected.
                if (detail.getCalculatedPrice() == null) {
                    detail.setCalculatedPrice(calculateDetailPrice(detailRequest.getCargoTypePriceId(),
                            detailRequest.getDimensionVol(), detailRequest.getQuantity()));
                }
            } else {
                detail.setCalculatedPrice(calculateDetailPrice(detailRequest.getCargoTypePriceId(),
                        detailRequest.getDimensionVol(), detailRequest.getQuantity()));
            }
            replacements.add(detail);
        }

        cargoTicketDetailRepository.deleteAll(existingById.values());
        cargoTicketDetailRepository.saveAll(replacements);
        cargoTicketDetailRepository.flush();
        if (!(cargoTicketPaymentPolicy.isCompleted(existingPayment) && !freightInputsChanged)) {
            updateTicketTotalPrice(ticket);
        }
        return mapToResponse(ticket);
    }

    @Override
    @Transactional
    /**
     * Creates the cargo ticket detail.
     *
     * @param ticketId the value supplied for this operation
     * @param request  the value supplied for this operation
     *
     * @return the created cargo ticket detail
     */
    public CargoTicketDetailResponse createCargoTicketDetail(int ticketId, CargoTicketDetailRequest request) {
        CargoTicket ticket = cargoTicketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn gửi hàng."));
        requirePending(ticket, "Chỉ đơn đang chờ mới được thêm hàng hóa.");
        cargoTicketPaymentPolicy.rejectMoneyChangesWhenPaid(cargoTicketPaymentPolicy.findPayment(ticket));
        BigDecimal addedVolume = CargoVolumePolicy.occupiedVolume(
                request.getDimensionVol(), request.getQuantity());
        BigDecimal nextOrderVolume = cargoTicketDetailRepository.sumVolumeByCargoTicketId(ticketId)
                .add(addedVolume);
        CargoVolumePolicy.validateOrderVolume(nextOrderVolume);
        validateCapacity(ticket.getTripId(), addedVolume, BigDecimal.ZERO);

        BigDecimal calcPrice = calculateDetailPrice(request.getCargoTypePriceId(), request.getDimensionVol(),
                request.getQuantity());

        CargoTicketDetail detail = CargoTicketDetail.builder()
                .cargoTicket(ticket)
                .cargoTypePriceId(request.getCargoTypePriceId())
                .description(request.getDescription())
                .quantity(request.getQuantity())
                .weightKg(request.getWeightKg())
                .dimensionVol(request.getDimensionVol())
                .calculatedPrice(calcPrice)
                .build();

        CargoTicketDetail saved = cargoTicketDetailRepository.save(detail);

        cargoTicketDetailRepository.flush();
        updateTicketTotalPrice(ticket);

        return CargoTicketDetailResponse.builder()
                .cargoTicketDetailId(saved.getCargoTicketDetailId())
                .cargoTicketId(saved.getCargoTicket().getCargoTicketId())
                .cargoTypePriceId(saved.getCargoTypePriceId())
                .description(saved.getDescription())
                .quantity(saved.getQuantity())
                .weightKg(saved.getWeightKg())
                .dimensionVol(saved.getDimensionVol())
                .calculatedPrice(saved.getCalculatedPrice())
                .build();
    }

    @Override
    @Transactional
    /**
     * Updates the cargo ticket detail.
     *
     * @param detailId the value supplied for this operation
     * @param request  the value supplied for this operation
     *
     * @return the updated cargo ticket detail
     */
    public CargoTicketDetailResponse updateCargoTicketDetail(int detailId, CargoTicketDetailRequest request) {
        CargoTicketDetail detail = cargoTicketDetailRepository.findById(detailId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chi tiết đơn gửi hàng."));
        requirePending(detail.getCargoTicket(), "Chỉ đơn đang chờ mới được cập nhật hàng hóa.");
        cargoTicketPaymentPolicy.rejectMoneyChangesWhenPaid(
                cargoTicketPaymentPolicy.findPayment(detail.getCargoTicket()));
        BigDecimal previousVolume = occupiedVolume(detail);
        BigDecimal nextVolume = CargoVolumePolicy.occupiedVolume(
                request.getDimensionVol(), request.getQuantity());
        BigDecimal nextOrderVolume = cargoTicketDetailRepository
                .sumVolumeByCargoTicketId(detail.getCargoTicket().getCargoTicketId())
                .subtract(previousVolume)
                .add(nextVolume);
        CargoVolumePolicy.validateOrderVolume(nextOrderVolume);
        validateCapacity(detail.getCargoTicket().getTripId(), nextVolume, previousVolume);

        BigDecimal calcPrice = calculateDetailPrice(request.getCargoTypePriceId(), request.getDimensionVol(),
                request.getQuantity());

        detail.setCargoTypePriceId(request.getCargoTypePriceId());
        detail.setDescription(request.getDescription());
        detail.setQuantity(request.getQuantity());
        detail.setWeightKg(request.getWeightKg());
        detail.setDimensionVol(request.getDimensionVol());
        detail.setCalculatedPrice(calcPrice);

        CargoTicketDetail saved = cargoTicketDetailRepository.save(detail);

        cargoTicketDetailRepository.flush();
        updateTicketTotalPrice(detail.getCargoTicket());

        return CargoTicketDetailResponse.builder()
                .cargoTicketDetailId(saved.getCargoTicketDetailId())
                .cargoTicketId(saved.getCargoTicket().getCargoTicketId())
                .cargoTypePriceId(saved.getCargoTypePriceId())
                .description(saved.getDescription())
                .quantity(saved.getQuantity())
                .weightKg(saved.getWeightKg())
                .dimensionVol(saved.getDimensionVol())
                .calculatedPrice(saved.getCalculatedPrice())
                .build();
    }

    @Override
    @Transactional
    /**
     * Deletes the cargo ticket detail.
     *
     * @param detailId the value supplied for this operation
     */
    public void deleteCargoTicketDetail(int detailId) {
        CargoTicketDetail detail = cargoTicketDetailRepository.findById(detailId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chi tiết đơn gửi hàng."));
        CargoTicket ticket = detail.getCargoTicket();
        requirePending(ticket, "Chỉ đơn đang chờ mới được xóa hàng hóa.");
        cargoTicketPaymentPolicy.rejectMoneyChangesWhenPaid(cargoTicketPaymentPolicy.findPayment(ticket));
        cargoTicketDetailRepository.delete(detail);

        cargoTicketDetailRepository.flush();
        updateTicketTotalPrice(ticket);
    }

    @Override
    @Transactional
    /**
     * Executes the disable operation.
     *
     * @param id the value supplied for this operation
     */
    public void disable(int id) {
        CargoTicket ticket = findByIdOrThrow(id);
        requirePending(ticket, "Chỉ đơn đang chờ mới được hủy.");
        cargoTicketPaymentPolicy.applyCancelPaymentSideEffects(ticket);
        ticket.setStatus("CANCELLED");
        cargoTicketRepository.save(ticket);
    }

    @Override
    @Transactional
    /**
     * Completes the destination ticket-office hand-off after trip staff unload.
     *
     * @param id        cargo ticket identifier
     * @param accountId authenticated destination ticket-staff account
     */
    public void confirmReceived(int id, Integer accountId, ConfirmReceivedRequest request) {
        Staff currentStaff = requireAgencyStaff(accountId);
        CargoTicket ticket = requireDestinationArrivedTicket(id, currentStaff);
        String paymentMethod = request != null ? request.getPaymentMethod() : null;
        if (CargoTicketPaymentPolicy.FEE_RECEIVER.equalsIgnoreCase(ticket.getFeePayer())
                && paymentMethod != null) {
            ensureReceiverPayment(ticket, paymentMethod);
        }
        cargoTicketPaymentPolicy.settleReceiverPaymentBeforeDeliver(ticket);
        ticket.setStatus("DELIVERED");
        ticket.setDeliveredBy(currentStaff);
        cargoTicketRepository.save(ticket);
    }

    @Override
    @Transactional
    public CargoTicketResponse chooseReceiverPaymentMethod(
            int id, ReceiverPaymentMethodRequest request, Integer accountId) {
        Staff currentStaff = requireAgencyStaff(accountId);
        CargoTicket ticket = requireDestinationArrivedTicket(id, currentStaff);
        if (!CargoTicketPaymentPolicy.FEE_RECEIVER.equalsIgnoreCase(ticket.getFeePayer())) {
            throw new BusinessRuleException("Chỉ đơn người nhận trả phí mới chọn hình thức tại văn phòng đích.");
        }
        Payment payment = ensureReceiverPayment(ticket, request.getPaymentMethod());
        if (CargoTicketPaymentPolicy.METHOD_CASH.equals(payment.getPaymentMethod())
                && CargoTicketPaymentPolicy.STATUS_PENDING.equals(payment.getStatus())) {
            // Cash is completed together with confirm-received, not here.
        }
        return mapToResponse(ticket);
    }

    private CargoTicket requireDestinationArrivedTicket(int id, Staff currentStaff) {
        TicketAgency agency = ticketAgencyRepository
                .findByTicketAgencyIdAndIsActiveTrue(currentStaff.getTicketAgencyId())
                .orElseThrow(() -> new BusinessRuleException("Văn phòng vé của nhân viên không hoạt động."));
        CargoTicket ticket = findByIdOrThrow(id);
        if (agency.getStopPointId() != ticket.getDropoffStopId()) {
            throw new BusinessRuleException("Đơn hàng không thuộc văn phòng nhận của nhân viên.");
        }
        if (!"ARRIVED".equals(ticket.getStatus())) {
            throw new BusinessRuleException("Chỉ đơn đã đến nơi mới có thể xác nhận nhận hàng.");
        }
        return ticket;
    }

    private Payment ensureReceiverPayment(CargoTicket ticket, String paymentMethod) {
        if (paymentMethod == null || paymentMethod.isBlank()) {
            throw new BusinessRuleException("Phải chọn tiền mặt hoặc chuyển khoản.");
        }
        Payment payment = cargoTicketPaymentPolicy.findPayment(ticket);
        if (payment != null && cargoTicketPaymentPolicy.isCompleted(payment)) {
            return payment;
        }
        if (payment == null) {
            payment = Payment.builder()
                    .cargoTicket(ticket)
                    .amount(ticket.getTotalPrice())
                    .paymentMethod(paymentMethod)
                    .transactionId(transactionIdGenerator.generateUniqueTransactionId())
                    .status(CargoTicketPaymentPolicy.STATUS_PENDING)
                    .refundAmount(BigDecimal.ZERO)
                    .build();
        } else {
            payment.setPaymentMethod(paymentMethod);
        }
        return paymentRepository.save(payment);
    }

    @Override
    /**
     * Searches for contacts records.
     *
     * @param phone the value supplied for this operation
     *
     * @return the matching results
     */
    public List<CustomerContactResponse> searchContacts(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return List.of();
        }
        List<Object[]> results = cargoTicketRepository.findContactsByPhoneNative(phone.trim());
        return results.stream()
                .map(obj -> CustomerContactResponse.builder()
                        .phone(obj[0] != null ? obj[0].toString() : null)
                        .name(obj[1] != null ? obj[1].toString() : null)
                        .build())
                .toList();
    }

    @Override
    @Transactional
    /**
     * Completes the payment.
     *
     * @param cargoTicketId the value supplied for this operation
     */
    public void completePayment(int cargoTicketId) {
        CargoTicket ticket = findByIdOrThrow(cargoTicketId);
        Payment payment = cargoTicketPaymentPolicy.requirePayment(ticket);
        cargoTicketPaymentPolicy.markCashCompleted(payment);
    }

    @Override
    /**
     * Returns the trips by stops in order.
     *
     * @param request the value supplied for this operation
     *
     * @return the trips by stops in order
     */
    public List<TripByStopResponse> getTripsByStopsInOrder(TripByStopRequest request) {
        return tripRepository.findTripsByStopsInOrder(request.getPickupStopId(), request.getDropoffStopId())
                .stream()
                .map(t -> TripByStopResponse.builder()
                        .tripId(t.getTripId())
                        .routeId(t.getRouteId())
                        .coachId(t.getCoachId())
                        .coachTypeName(t.getCoach() != null && t.getCoach().getCoachType() != null
                                ? t.getCoach().getCoachType().getCoachTypeName()
                                : null)
                        .departureTime(t.getDepartureTime())
                        .status(t.getStatus())
                        .build())
                .toList();
    }

    @Override
    public CargoTicketDetailPriceResponse calculatePrice(
            CargoTicketDetailPriceRequest request) {
        BigDecimal price = calculateDetailPrice(request.getCargoTypePriceId(), request.getDimensionVol(),
                request.getQuantity());
        return new com.ralsei.dto.response.cargoticketdetail.CargoTicketDetailPriceResponse(price);
    }

    @Override
    public CargoAssignableBoardResponse getAssignableCargo(int tripId, Integer accountId) {
        Staff currentStaff = requireAgencyStaff(accountId);
        requireTripOpenForCargo(tripId);

        BigDecimal used = cargoTicketDetailRepository.sumActiveVolumeByTripId(tripId);
        if (used == null) {
            used = BigDecimal.ZERO;
        }

        List<CargoAssignableTicketResponse> tickets = cargoTicketRepository
                .findUnassignedReceivedByAgency(currentStaff.getTicketAgencyId())
                .stream()
                .filter(ticket -> isAssignableToTrip(tripId, ticket, currentStaff.getTicketAgencyId()))
                .filter(cargoTicketPaymentPolicy::isReadyForTripAssignment)
                .filter(this::hasPositiveCargoVolume)
                .map(this::mapToAssignableResponse)
                .toList();

        return CargoAssignableBoardResponse.builder()
                .tripId(tripId)
                .usedCargoVolume(used)
                .cargoCapacity(CARGO_CAPACITY_M3)
                .tickets(tickets)
                .build();
    }

    @Override
    @Transactional
    public CargoTripAssignResponse assignCargoToTrip(
            int tripId, CargoTripAssignRequest request, Integer accountId) {
        Staff currentStaff = requireAgencyStaff(accountId);
        requireTripOpenForCargo(tripId);

        Set<Integer> uniqueIds = new LinkedHashSet<>(request.getCargoTicketIds());
        List<CargoTicket> selected = new ArrayList<>();
        BigDecimal selectedVolume = BigDecimal.ZERO;

        for (Integer cargoTicketId : uniqueIds) {
            CargoTicket ticket = findByIdOrThrow(cargoTicketId);
            requirePending(ticket, "Chỉ đơn đang chờ mới được gán chuyến.");
            if (ticket.getTripId() != null) {
                throw new BusinessRuleException(
                        "Đơn " + ticket.getTicketCode() + " đã được gán cho một chuyến xe.");
            }
            requireSellerAgency(ticket, currentStaff.getTicketAgencyId());
            if (!isAssignableToTrip(tripId, ticket, currentStaff.getTicketAgencyId())) {
                throw new BusinessRuleException(
                        "Đơn " + ticket.getTicketCode() + " không phù hợp với chuyến xe đã chọn.");
            }
            cargoTicketPaymentPolicy.requireReadyForTripAssignment(ticket);
            BigDecimal volume = cargoTicketDetailRepository.sumVolumeByCargoTicketId(cargoTicketId);
            if (volume == null) {
                volume = BigDecimal.ZERO;
            }
            if (volume.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessRuleException(
                        "Đơn " + ticket.getTicketCode() + " chưa có hàng hóa hợp lệ để gán chuyến.");
            }
            selectedVolume = selectedVolume.add(volume);
            selected.add(ticket);
        }

        validateCapacity(tripId, selectedVolume, BigDecimal.ZERO);

        for (CargoTicket ticket : selected) {
            ticket.setTripId(tripId);
        }
        cargoTicketRepository.saveAll(selected);

        BigDecimal used = cargoTicketDetailRepository.sumActiveVolumeByTripId(tripId);
        if (used == null) {
            used = BigDecimal.ZERO;
        }

        return CargoTripAssignResponse.builder()
                .tripId(tripId)
                .assignedCount(selected.size())
                .usedCargoVolume(used)
                .cargoCapacity(CARGO_CAPACITY_M3)
                .assignedTickets(selected.stream().map(this::mapToResponse).toList())
                .build();
    }

    private boolean isAssignableToTrip(int tripId, CargoTicket ticket, int ticketAgencyId) {
        return tripRepository.isEligibleForAgencyCargo(
                tripId, ticket.getPickupStopId(), ticket.getDropoffStopId(), ticketAgencyId);
    }

    private boolean hasPositiveCargoVolume(CargoTicket ticket) {
        BigDecimal volume = cargoTicketDetailRepository.sumVolumeByCargoTicketId(ticket.getCargoTicketId());
        return volume != null && volume.compareTo(BigDecimal.ZERO) > 0;
    }

    private void requireSellerAgency(CargoTicket ticket, int ticketAgencyId) {
        Staff seller = ticket.getSoldBy();
        if (seller == null || seller.getTicketAgencyId() == null
                || seller.getTicketAgencyId() != ticketAgencyId) {
            throw new BusinessRuleException(
                    "Đơn " + ticket.getTicketCode() + " không thuộc văn phòng vé hiện tại.");
        }
    }

    private CargoAssignableTicketResponse mapToAssignableResponse(CargoTicket ticket) {
        BigDecimal volume = cargoTicketDetailRepository.sumVolumeByCargoTicketId(ticket.getCargoTicketId());
        if (volume == null) {
            volume = BigDecimal.ZERO;
        }
        String pickupStopName = coachStopRepository.findById(ticket.getPickupStopId())
                .map(stop -> stop.getStopPointName()).orElse(null);
        String dropoffStopName = coachStopRepository.findById(ticket.getDropoffStopId())
                .map(stop -> stop.getStopPointName()).orElse(null);
        Payment payment = cargoTicketPaymentPolicy.findPayment(ticket);
        return CargoAssignableTicketResponse.builder()
                .cargoTicketId(ticket.getCargoTicketId())
                .ticketCode(ticket.getTicketCode())
                .senderName(ticket.getSenderName())
                .senderPhone(ticket.getSenderPhone())
                .receiverName(ticket.getReceiverName())
                .receiverPhone(ticket.getReceiverPhone())
                .totalPrice(ticket.getTotalPrice())
                .pickupStopId(ticket.getPickupStopId())
                .pickupStopName(pickupStopName)
                .dropoffStopId(ticket.getDropoffStopId())
                .dropoffStopName(dropoffStopName)
                .status(ticket.getStatus())
                .feePayer(ticket.getFeePayer())
                .paymentMethod(payment != null ? payment.getPaymentMethod() : null)
                .paymentStatus(payment != null ? payment.getStatus() : null)
                .occupiedVolume(volume)
                .build();
    }

    private BigDecimal calculateDetailPrice(int cargoTypePriceId, BigDecimal dimensionVol, int quantity) {
        CargoTypePrice price = cargoTypePriceRepository.findById(cargoTypePriceId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn giá cước hàng hóa."));
        BigDecimal unitPrice = FreightCalculatorUtility.calculatePriceWithSurcharge(dimensionVol,
                price.getPricePerUnit());
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    private void updateTicketTotalPrice(CargoTicket ticket) {
        List<CargoTicketDetail> details = cargoTicketDetailRepository
                .findByCargoTicket_CargoTicketId(ticket.getCargoTicketId());
        BigDecimal total = details.stream()
                .map(CargoTicketDetail::getCalculatedPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        ticket.setTotalPrice(total);
        cargoTicketRepository.save(ticket);

        Payment payment = cargoTicketPaymentPolicy.findPayment(ticket);
        cargoTicketPaymentPolicy.syncAmountIfPending(payment, total);
    }

    private void guardPaidOrderMoneyFields(
            CargoTicket ticket, Payment payment, CargoTicketRequest request, BigDecimal nextTotal) {
        guardPaidOrderFeeFields(ticket, payment, request);
        guardPaidOrderAmount(payment, nextTotal);
    }

    /**
     * AF-2: paid orders cannot change fee payer or payment method. COD is
     * independent of freight.
     */
    private void guardPaidOrderFeeFields(
            CargoTicket ticket, Payment payment, CargoTicketRequest request) {
        if (!cargoTicketPaymentPolicy.isCompleted(payment)) {
            return;
        }
        if (request.getFeePayer() != null
                && !request.getFeePayer().equalsIgnoreCase(ticket.getFeePayer())) {
            cargoTicketPaymentPolicy.rejectMoneyChangesWhenPaid(payment);
        }
        if (request.getPaymentMethod() != null
                && !request.getPaymentMethod().equalsIgnoreCase(payment.getPaymentMethod())) {
            cargoTicketPaymentPolicy.rejectMoneyChangesWhenPaid(payment);
        }
    }

    /** AF-2: paid orders cannot change freight amount. */
    private void guardPaidOrderAmount(Payment payment, BigDecimal nextTotal) {
        if (!cargoTicketPaymentPolicy.isCompleted(payment)) {
            return;
        }
        if (nextTotal != null && payment.getAmount().compareTo(nextTotal) != 0) {
            cargoTicketPaymentPolicy.rejectMoneyChangesWhenPaid(payment);
        }
    }

    /**
     * Freight amount depends on cargo type price, quantity, and volume only.
     * Trip / contact / COD / weight-only edits must not look like money changes.
     */
    private boolean sameFreightInputs(
            List<CargoTicketDetail> existing, List<CargoTicketDetailRequest> requested) {
        if (existing == null || requested == null || existing.size() != requested.size()) {
            return false;
        }
        var existingById = existing.stream().collect(java.util.stream.Collectors.toMap(
                CargoTicketDetail::getCargoTicketDetailId, detail -> detail));
        for (CargoTicketDetailRequest detailRequest : requested) {
            if (detailRequest.getCargoTicketDetailId() == null) {
                return false;
            }
            CargoTicketDetail current = existingById.remove(detailRequest.getCargoTicketDetailId());
            if (current == null) {
                return false;
            }
            if (current.getCargoTypePriceId() != detailRequest.getCargoTypePriceId()) {
                return false;
            }
            if (current.getQuantity() != detailRequest.getQuantity()) {
                return false;
            }
            if (!sameVolume(current.getDimensionVol(), detailRequest.getDimensionVol())) {
                return false;
            }
        }
        return existingById.isEmpty();
    }

    private boolean sameVolume(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return false;
        }
        // Tolerate JSON/JS float noise around stored DECIMAL volumes.
        return left.setScale(6, java.math.RoundingMode.HALF_UP)
                .compareTo(right.setScale(6, java.math.RoundingMode.HALF_UP)) == 0;
    }

    private void validateReferences(CargoTicketRequest request) {
        validateReferences(request, null);
    }

    private void validateReferences(CargoTicketRequest request, Staff currentStaff) {
        if (request.getPickupStopId() == request.getDropoffStopId()) {
            throw new BusinessRuleException("Điểm nhận và điểm trả hàng phải khác nhau.");
        }
        if (request.getSenderPhone() != null && request.getReceiverPhone() != null
                && request.getSenderPhone().trim().equals(request.getReceiverPhone().trim())) {
            throw new BusinessRuleException("Số điện thoại người gửi và người nhận không được trùng nhau.");
        }
        if (request.getTripId() != null && !tripRepository.existsById(request.getTripId())) {
            throw new ResourceNotFoundException("Không tìm thấy chuyến đi có ID là: " + request.getTripId());
        }
        if (request.getCustomerId() != null && !customerRepository.existsById(request.getCustomerId())) {
            throw new ResourceNotFoundException("Không tìm thấy khách hàng có ID là: " + request.getCustomerId());
        }
        requireStop(request.getPickupStopId());
        requireStop(request.getDropoffStopId());
        if (!ticketAgencyRepository.existsByStopPointIdAndIsActiveTrue(request.getPickupStopId())) {
            throw new BusinessRuleException("Điểm nhận hàng không có văn phòng vé đang hoạt động.");
        }
        if (!ticketAgencyRepository.existsByStopPointIdAndIsActiveTrue(request.getDropoffStopId())) {
            throw new BusinessRuleException("Điểm trả hàng không có văn phòng vé đang hoạt động.");
        }
        requireStaff(request.getSoldBy());
        requireStaff(request.getLoadedBy());
        requireStaff(request.getUnloadedBy());
        requireStaff(request.getDeliveredBy());

        boolean eligible = request.getTripId() != null && currentStaff != null
                ? tripRepository.isEligibleForAgencyCargo(request.getTripId(), request.getPickupStopId(),
                        request.getDropoffStopId(), currentStaff.getTicketAgencyId())
                : request.getTripId() == null || tripRepository.isEligibleForCargo(
                        request.getTripId(), request.getPickupStopId(), request.getDropoffStopId());
        if (!eligible) {
            throw new BusinessRuleException("Chuyến xe không còn phù hợp với điểm nhận và điểm trả đã chọn.");
        }
        requireStaffPosition(request.getSoldBy(), "TICKET_STAFF", "Nhân viên bán vé");
        requireHandlerPosition(request.getLoadedBy(), "Nhân viên xếp hàng");
        requireHandlerPosition(request.getUnloadedBy(), "Nhân viên dỡ hàng");
        requireStaffPosition(request.getDeliveredBy(), "DRIVER", "Nhân viên giao hàng");
    }

    private Staff requireAgencyStaff(Integer accountId) {
        Staff staff = staffRepository.findByAccountId(accountId)
                .orElseThrow(() -> new BusinessRuleException("Tài khoản chưa được gán cho nhân viên."));
        if (!staff.isActive() || staff.getTicketAgencyId() == null) {
            throw new BusinessRuleException("Nhân viên chưa được gán cho văn phòng vé đang hoạt động.");
        }
        return staff;
    }

    /**
     * When a trip is present, rejects every trip that has already started or
     * otherwise left the scheduled state. Null tripId is allowed so staff can
     * create (or keep) an order before assigning a coach.
     *
     * @param tripId trip selected by ticket staff, or null when deferred
     */
    private void requireTripOpenForCargo(Integer tripId) {
        if (tripId == null) {
            return;
        }
        var trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chuyến đi có ID là: " + tripId));
        if (!"SCHEDULED".equals(trip.getStatus())) {
            throw new BusinessRuleException(
                    "Chuyến xe đã khởi hành hoặc không còn hoạt động, không thể nhận thêm hàng.");
        }
    }

    /**
     * Replaces client-owned create values with facts controlled by the staff
     * workflow. The pickup office and seller come from the authenticated account.
     * COD and order-level description are taken from the form (COD defaults to 0
     * when omitted).
     *
     * @param request      cargo order being created
     * @param currentStaff authenticated ticket-office staff member
     */
    private void applyCreateBusinessDefaults(CargoTicketRequest request, Staff currentStaff) {
        TicketAgency agency = ticketAgencyRepository
                .findByTicketAgencyIdAndIsActiveTrue(currentStaff.getTicketAgencyId())
                .orElseThrow(() -> new BusinessRuleException("Văn phòng vé của nhân viên không hoạt động."));
        request.setPickupStopId(agency.getStopPointId());
        request.setSoldBy(currentStaff);
        if (request.getCodAmount() == null) {
            request.setCodAmount(BigDecimal.ZERO);
        }
    }

    private void requireStop(int id) {
        if (!coachStopRepository.existsById(id)) {
            throw new ResourceNotFoundException("Không tìm thấy điểm dừng có ID là: " + id);
        }
    }

    private void requireStaff(Staff staff) {
        if (staff != null && !staffRepository.existsById(staff.getStaffId())) {
            throw new ResourceNotFoundException("Không tìm thấy nhân viên có ID là: " + staff.getStaffId());
        }
    }

    private void requireStaffPosition(Staff staff, String position, String fieldName) {
        if (staff != null
                && !staffRepository.existsByStaffIdAndIsActiveTrueAndStaffPosition(staff.getStaffId(), position)) {
            throw new BusinessRuleException(fieldName + " không đúng chức vụ yêu cầu.");
        }
    }

    private void requireHandlerPosition(Staff staff, String fieldName) {
        if (staff != null && !staffRepository.existsByStaffIdAndIsActiveTrueAndStaffPositionIn(
                staff.getStaffId(), List.of("ATTENDANT", "TICKET_STAFF"))) {
            throw new BusinessRuleException(fieldName + " phải là phụ xe hoặc nhân viên bán vé.");
        }
    }

    private String generateTicketCode() {
        String code;
        do {
            code = "CG-" + LocalDate.now().toString().replace("-", "") + "-"
                    + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        } while (cargoTicketRepository.existsByTicketCodeIgnoreCase(code));
        return code;
    }

    private CargoTicket findByIdOrThrow(int id) {
        return cargoTicketRepository.findByCargoTicketIdAndStatusNot(id, STATUS_ABANDONED).orElseThrow(
                () -> new ResourceNotFoundException("Không tìm thấy đơn gửi hàng có ID là: " + id));
    }

    private void requirePending(CargoTicket ticket, String message) {
        if (!"RECEIVED".equals(ticket.getStatus())) {
            throw new BusinessRuleException(message);
        }
    }

    void newCargoTicketValidation_onlyForTest(CargoTicketRequest request, Staff currentStaff) {
        validateReferences(request, currentStaff);
    }

    /**
     * Prevents a stale browser from overbooking a coach after its list was
     * loaded. The replacement volume is subtracted for detail updates.
     * Orders without a trip skip capacity until a coach is assigned.
     */
    private void validateCapacity(Integer tripId, BigDecimal addedVolume, BigDecimal replacedVolume) {
        if (tripId == null) {
            return;
        }
        BigDecimal occupied = cargoTicketDetailRepository.sumActiveVolumeByTripId(tripId);
        BigDecimal nextOccupied = (occupied == null ? BigDecimal.ZERO : occupied)
                .subtract(replacedVolume).add(addedVolume);
        if (nextOccupied.compareTo(CARGO_CAPACITY_M3) > 0) {
            throw new BusinessRuleException("Khoang hàng của chuyến xe vượt quá sức chứa 2,5 m³.");
        }
    }

    /**
     * Calculates the physical volume occupied by all packages in one detail row.
     */
    private BigDecimal occupiedVolume(CargoTicketDetail detail) {
        return CargoVolumePolicy.occupiedVolume(detail.getDimensionVol(), detail.getQuantity());
    }

    private void copyRequest(CargoTicketRequest request, CargoTicket ticket, String ticketCode) {
        ticket.setTripId(request.getTripId());
        ticket.setCustomerId(request.getCustomerId());
        ticket.setSenderName(request.getSenderName().trim());
        ticket.setSenderPhone(request.getSenderPhone().trim());
        ticket.setReceiverName(request.getReceiverName().trim());
        ticket.setReceiverPhone(request.getReceiverPhone().trim());
        ticket.setTicketCode(ticketCode);
        ticket.setTotalPrice(request.getTotalPrice());
        ticket.setDescription(trimToNull(request.getDescription()));
        ticket.setFeePayer(request.getFeePayer());
        ticket.setCodAmount(request.getCodAmount());
        ticket.setPickupStopId(request.getPickupStopId());
        ticket.setDropoffStopId(request.getDropoffStopId());
        ticket.setStatus(request.getStatus());
        ticket.setSoldBy(staffRepository.findById(request.getSoldBy().getStaffId()).orElse(null));
        ticket.setLoadedBy(
                request.getLoadedBy() != null
                        ? staffRepository.findById(request.getLoadedBy().getStaffId()).orElse(null)
                        : null);
        ticket.setUnloadedBy(
                request.getUnloadedBy() != null
                        ? staffRepository.findById(request.getUnloadedBy().getStaffId()).orElse(null)
                        : null);
        ticket.setDeliveredBy(
                request.getDeliveredBy() != null
                        ? staffRepository.findById(request.getDeliveredBy().getStaffId()).orElse(null)
                        : null);
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private CargoTicketResponse mapToResponse(CargoTicket ticket) {
        String pickupStopName = coachStopRepository.findById(ticket.getPickupStopId())
                .map(stop -> stop.getStopPointName()).orElse(null);
        String dropoffStopName = coachStopRepository.findById(ticket.getDropoffStopId())
                .map(stop -> stop.getStopPointName()).orElse(null);

        Payment payment = paymentRepository.findByCargoTicket_CargoTicketId(ticket.getCargoTicketId()).orElse(null);
        var responsibility = cargoTicketRepository
                .findResponsibilityByCargoTicketId(ticket.getCargoTicketId()).orElse(null);
        Integer routeId = responsibility != null ? responsibility.getRouteId() : null;
        String routeName = responsibility != null ? responsibility.getRouteName() : null;
        if (routeId == null) {
            routeId = routeStopRepository.findRouteIdForPickupAndDropoff(
                    ticket.getPickupStopId(), ticket.getDropoffStopId());
            if (routeId != null && routeName == null) {
                routeName = routeRepository.findById(routeId)
                        .map(route -> route.getRouteName())
                        .orElse(null);
            }
        }

        return CargoTicketResponse.builder()
                .cargoTicketId(ticket.getCargoTicketId())
                .tripId(ticket.getTripId())
                .customerId(ticket.getCustomerId())
                .senderName(ticket.getSenderName())
                .senderPhone(ticket.getSenderPhone())
                .receiverName(ticket.getReceiverName())
                .receiverPhone(ticket.getReceiverPhone())
                .ticketCode(ticket.getTicketCode())
                .totalPrice(ticket.getTotalPrice())
                .description(ticket.getDescription())
                .feePayer(ticket.getFeePayer())
                .codAmount(ticket.getCodAmount())
                .pickupStopId(ticket.getPickupStopId())
                .pickupStopName(pickupStopName)
                .dropoffStopId(ticket.getDropoffStopId())
                .dropoffStopName(dropoffStopName)
                .status(ticket.getStatus())
                .routeId(routeId)
                .routeName(routeName)
                .licensePlate(responsibility == null ? null : responsibility.getLicensePlate())
                .destinationAgencyName(responsibility == null ? null : responsibility.getDestinationAgencyName())
                .driverName(responsibility == null ? null : responsibility.getDriverName())
                .driverPhone(responsibility == null ? null : responsibility.getDriverPhone())
                .driverCccd(responsibility == null ? null : responsibility.getDriverCccd())
                .attendantName(responsibility == null ? null : responsibility.getAttendantName())
                .attendantPhone(responsibility == null ? null : responsibility.getAttendantPhone())
                .attendantCccd(responsibility == null ? null : responsibility.getAttendantCccd())
                .soldBy(ticket.getSoldBy())
                .loadedBy(ticket.getLoadedBy())
                .unloadedBy(ticket.getUnloadedBy())
                .deliveredBy(ticket.getDeliveredBy())
                .payment(payment)
                .qrUrl(buildQrUrl(payment, ticket.getTotalPrice()))
                .createdAt(ticket.getCreatedAt())
                .updatedAt(ticket.getUpdatedAt())
                .build();
    }

    private String buildQrUrl(Payment payment, java.math.BigDecimal totalPrice) {
        if (payment == null || !"BANK_TRANSFER".equals(payment.getPaymentMethod())
                || !"PENDING".equals(payment.getStatus())) {
            return null;
        }
        if (sepayBankAccount == null || sepayBankAccount.isBlank()
                || sepayBankName == null || sepayBankName.isBlank()
                || totalPrice == null || payment.getTransactionId() == null) {
            return null;
        }
        String amount = totalPrice.stripTrailingZeros().toPlainString();
        return "https://qr.sepay.vn/img?bank=" + encodeQrParam(sepayBankName)
                + "&acc=" + encodeQrParam(sepayBankAccount)
                + "&template=compact"
                + "&amount=" + encodeQrParam(amount)
                + "&des=" + encodeQrParam(payment.getTransactionId());
    }

    private static String encodeQrParam(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
