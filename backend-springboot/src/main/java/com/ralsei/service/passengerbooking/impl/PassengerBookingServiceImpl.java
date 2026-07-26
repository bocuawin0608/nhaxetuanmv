package com.ralsei.service.passengerbooking.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.ralsei.dto.request.passengerbooking.BookingConfirmRequest;
import com.ralsei.dto.request.passengerbooking.PassengerDTO;
import com.ralsei.dto.request.passengerbooking.PriceCalculationRequest;
import com.ralsei.dto.request.passengerbooking.SeatLockRequest;
import com.ralsei.dto.request.payment.PaymentCheckoutRequest;
import com.ralsei.dto.response.passengerbooking.BookingConfirmResponse;
import com.ralsei.dto.response.passengerbooking.BookingPaymentPageResponse;
import com.ralsei.dto.response.passengerbooking.CheckPhoneResponse;
import com.ralsei.dto.response.passengerbooking.CoachStopDropdownDTO;
import com.ralsei.dto.response.passengerbooking.CustomerProfileDTO;
import com.ralsei.dto.response.passengerbooking.PriceCalculationResponse;
import com.ralsei.dto.response.passengerbooking.SeatLockResponse;
import com.ralsei.dto.response.passengerbooking.Step2InitResponse;
import com.ralsei.dto.response.passengerbooking.TripSeatResponse;
import com.ralsei.dto.response.passengerbooking.VoucherDTO;
import com.ralsei.exception.BusinessRuleException;
import com.ralsei.exception.ResourceNotFoundException;
import com.ralsei.model.AccompaniedChild;
import com.ralsei.model.Account;
import com.ralsei.model.CoachStop;
import com.ralsei.model.Customer;
import com.ralsei.model.PassengerTicket;
import com.ralsei.model.PassengerTicketDetail;
import com.ralsei.model.Payment;
import com.ralsei.model.RouteStop;
import com.ralsei.model.Trip;
import com.ralsei.model.TripSeat;
import com.ralsei.model.Voucher;
import com.ralsei.model.enums.PassengerTicketDetailStatus;
import com.ralsei.model.enums.PassengerTicketStatus;
import com.ralsei.model.enums.TripSeatStatus;
import com.ralsei.model.enums.VoucherType;
import com.ralsei.repository.AccompaniedChildRepository;
import com.ralsei.repository.AccountRepository;
import com.ralsei.repository.CustomerRepository;
import com.ralsei.repository.PassengerTicketDetailRepository;
import com.ralsei.repository.PassengerTicketRepository;
import com.ralsei.repository.PaymentRepository;
import com.ralsei.repository.RouteStopRepository;
import com.ralsei.repository.TripRepository;
import com.ralsei.repository.TripSeatRepository;
import com.ralsei.service.JwtService;
import com.ralsei.service.PaymentService;
import com.ralsei.service.RouteStopService;
import com.ralsei.service.VoucherService;
import com.ralsei.service.passengerbooking.PassengerBookingService;
import com.ralsei.service.passengerbooking.PassengerPendingPaymentService;
import com.ralsei.service.passengerbooking.PassengerPhoneVerificationService;
import com.ralsei.service.passengerbooking.PaymentSseService;
import com.ralsei.service.passengerbooking.SeatHoldService;
import com.ralsei.service.ticketgenerator.TicketCodeGenerator;
import com.ralsei.util.PhoneNumberUtility;
import com.ralsei.util.PiiMaskingUtility;
import com.ralsei.util.validation.BookingValidationPatterns;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
/**
 * Provides the passenger booking service impl component for the application.
 */
public class PassengerBookingServiceImpl implements PassengerBookingService {

    private final TripRepository tripRepo;
    private final TripSeatRepository tripSeatRepo;
    private final RouteStopRepository routeStopRepo;
    private final AccountRepository accountRepo;
    private final CustomerRepository customerRepo;
    private final PassengerTicketRepository ticketRepo;
    private final PassengerTicketDetailRepository ticketDetailRepo;
    private final AccompaniedChildRepository accompaniedChildRepo;

    private final SeatHoldService seatHoldService;
    private final RouteStopService routeStopService;
    private final VoucherService voucherService;
    private final PaymentService paymentService;
    private final PaymentRepository paymentRepository;
    private final PaymentSseService paymentSseService;
    private final PassengerPendingPaymentService passengerPendingPaymentService;
    private final PassengerPhoneVerificationService passengerPhoneVerificationService;
    private final TicketCodeGenerator ticketCodeGenerator;
    private final JwtService jwtService;

    private static final long BROWSING_HOLD_TTL_SECONDS = 600;
    private static final long PAYMENT_HOLD_TTL_SECONDS = 900;
    private static final String BANK_TRANSFER_METHOD = "BANK_TRANSFER";

    @Value("${sepay.bank.account}")
    private String sepayBankAccount;

    @Value("${sepay.bank.name}")
    private String sepayBankName;
    
    @Transactional(readOnly = true)
    @Override
    /**
     * Returns the seat map.
     *
     * @param tripId the value supplied for this operation
     *
     * @return the seat map
     */
    public List<TripSeatResponse> getSeatMap(Integer tripId) {
        assertTripBookable(tripId);

        List<TripSeatResponse> tripSeats = tripSeatRepo.getSeatMap(tripId);
        
        return tripSeats.stream().map(seat -> {
            return seatHoldService.isSeatLocked(seat.tripSeatId()) 
                ? seat.toBuilder().status(TripSeatStatus.LOCKED).build() : seat;
        }).toList();
    }

    @Transactional
    @Override
    /**
     * Executes the lock seats operation.
     *
     * @param tripId the value supplied for this operation
     * @param request the value supplied for this operation
     * @param holdToken the value supplied for this operation
     *
     * @return the operation result
     */
    public SeatLockResponse lockSeats(Integer tripId, SeatLockRequest request, String holdToken) {
        assertTripBookable(tripId);

        List<Integer> availableSeatIds = tripSeatRepo.findTripSeatIdsByTripIdAndStatus(tripId, TripSeatStatus.AVAILABLE);
        if(availableSeatIds.isEmpty() || !availableSeatIds.containsAll(request.tripSeatIds())) {
            throw new BusinessRuleException("Có ghế ngồi không hợp lệ hoặc đã được đặt! Vui lòng chọn lại!");
        }
        
        if(!seatHoldService.lockSeats(request.tripSeatIds(), holdToken, BROWSING_HOLD_TTL_SECONDS)) {
            throw new BusinessRuleException("Có ghế ngồi đã được đặt! Vui lòng chọn lại!");
        }

        return new SeatLockResponse(
            request.tripSeatIds(),
            holdToken,
            LocalDateTime.now().plusSeconds(BROWSING_HOLD_TTL_SECONDS)
        );
    }

    @Transactional
    @Override
    /**
     * Executes the release seats operation.
     *
     * @param tripSeatIds the value supplied for this operation
     * @param holdToken the value supplied for this operation
     *
     * @return the operation result
     */
    public boolean releaseSeats(List<Integer> tripSeatIds, String holdToken) {
        if (tripSeatIds == null || tripSeatIds.isEmpty()) {
            return false; 
        }

        try {
            seatHoldService.releaseSeats(tripSeatIds, holdToken);
            return true;
        } catch (Exception e) {
            log.error("Có lỗi xảy ra khi dọn dẹp giải phóng ghế: ", e);
            return false;
        }
    }

    @Transactional
    @Override
    /**
     * Executes the release seats by becon operation.
     *
     * @param holdToken the value supplied for this operation
     *
     * @return the operation result
     */
    public boolean releaseSeatsByBecon(String holdToken) {
        if(holdToken == null || holdToken.isBlank()) {
            return false;
        }
            
        List<Integer> tripSeatIds = seatHoldService.getTripSeatIdsByToken(holdToken);
        return releaseSeats(tripSeatIds, holdToken);
    }

    @Transactional(readOnly = true)
    @Override
    /**
     * Returns the step2 init data.
     *
     * @param tripId the value supplied for this operation
     * @param holdToken the value supplied for this operation
     * @param accessToken the value supplied for this operation
     *
     * @return the step2 init data
     */
    public Step2InitResponse getStep2InitData(Integer tripId, String holdToken, String accessToken) {

        List<RouteStop> routeStops = routeStopService.getStopsByTripId(tripId);
        if(routeStops == null || routeStops.isEmpty()) {
            throw new BusinessRuleException("Có lỗi lấy dữ liệu chuyến xe. Vui lòng liên hệ nhà xe!");
        }

        String firstProvince = routeStops.get(0).getCoachStop().getCity().trim();
        String lastProvince = routeStops.get(routeStops.size() - 1).getCoachStop().getCity().trim();
        List<CoachStopDropdownDTO> pickupPoints = getStopPointDropdown(routeStops, firstProvince);
        List<CoachStopDropdownDTO> dropoffPoints = getStopPointDropdown(routeStops, lastProvince);

        List<VoucherDTO> eligibleVouchers = getEligibleVouchers(accessToken);

        PriceCalculationResponse priceData = calculatePrice(tripId, new PriceCalculationRequest(holdToken, null, null, null), accessToken);

        return new Step2InitResponse(
            pickupPoints,
            dropoffPoints,
            eligibleVouchers,
            priceData.totalRawPrice(),
            priceData.basePrice(),
            resolveCustomerProfile(accessToken)
        );
    }

    @Transactional(readOnly = true)
    @Override
    /**
     * Executes the check phone operation.
     *
     * @param phone the value supplied for this operation
     *
     * @return the operation result
     */
    public CheckPhoneResponse checkPhone(String phone) {
        return passengerPhoneVerificationService.checkPhone(phone);
    }

    @Transactional(readOnly = true)
    @Override
    /**
     * Executes the calculate price operation.
     *
     * @param tripId the value supplied for this operation
     * @param request the value supplied for this operation
     * @param accessToken the value supplied for this operation
     *
     * @return the operation result
     */
    public PriceCalculationResponse calculatePrice(Integer tripId, PriceCalculationRequest request, String accessToken) {

        CoreCalculationResult coreResult = performCorePriceCalculation(
            tripId, request.holdToken(), request.pickupStopId(), 
            request.dropoffStopId(), request.voucherId(), accessToken
        );

        return new PriceCalculationResponse(
            coreResult.basePrice(),
            coreResult.baseSurcharge(),
            coreResult.totalRawPrice(),
            coreResult.discountAmount(),
            coreResult.totalFinalPrice()
        );
    }

    @Transactional
    @Override
    /**
     * Executes the confirm booking operation.
     *
     * @param tripId the value supplied for this operation
     * @param request the value supplied for this operation
     * @param holdToken the value supplied for this operation
     * @param accessToken the value supplied for this operation
     *
     * @return the operation result
     */
    public BookingConfirmResponse confirmBooking(Integer tripId, BookingConfirmRequest request, String holdToken, String accessToken) {
        validatePassengerChildBirthYears(request.passengers());
        validatePassengerPhoneVerification(request.passengers());

        CoreCalculationResult coreResult = performCorePriceCalculation(
            tripId, holdToken, request.pickupStopId(), 
            request.dropoffStopId(), request.voucherId(), accessToken
        );

        validateRequestSeatsMatchLockedSeats(request, coreResult.lockedSeatIds());

        String voucherCodeSnapshot = processVoucherUsage(coreResult.voucherToBeUsed());

        Integer customerId = resolveCustomerId(accessToken);

        PassengerTicket ticket = saveMasterTicket(tripId, request, coreResult, customerId, voucherCodeSnapshot);

        saveTicketDetailsAndChildrenInBatch(ticket.getPassengerTicketId(), request.passengers(), coreResult);

        Payment payment = paymentService.initializePayment(new PaymentCheckoutRequest(
            ticket.getPassengerTicketId(),
            null, // cargoTicketId = null
            coreResult.totalFinalPrice(),
            BANK_TRANSFER_METHOD
        ));

        seatHoldService.extendLock(coreResult.lockedSeatIds(), holdToken, PAYMENT_HOLD_TTL_SECONDS);

        return new BookingConfirmResponse(
            ticket.getTicketCode(),
            payment.getTransactionId(),
            coreResult.totalFinalPrice(),
            sepayBankAccount, 
            sepayBankName,
            LocalDateTime.now().plusSeconds(PAYMENT_HOLD_TTL_SECONDS),
            payment.getCancelToken()
        );
    }

    @Transactional(readOnly = true)
    @Override
    /**
     * Returns the payment page.
     *
     * @param transactionId the value supplied for this operation
     * @param cancelToken the value supplied for this operation
     * @param accessToken the value supplied for this operation
     *
     * @return the payment page
     */
    public BookingPaymentPageResponse getPaymentPage(String transactionId, String cancelToken, String accessToken) {
        Payment payment = paymentService.getPaymentByTransactionId(transactionId);
        if (payment.getPassengerTicketId() == null) {
            throw new ResourceNotFoundException("Không tìm thấy thông tin thanh toán vé hành khách!");
        }

        PassengerTicket ticket = ticketRepo.findById(payment.getPassengerTicketId())
            .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vé với mã giao dịch: " + transactionId));

        List<PassengerTicketDetail> details = ticketDetailRepo.findByPassengerTicketId(ticket.getPassengerTicketId());
        if (details.isEmpty()) {
            throw new ResourceNotFoundException("Không tìm thấy chi tiết vé!");
        }

        PassengerTicketDetail primaryDetail = details.get(0);
        List<String> seatCodes = details.stream()
            .map(ticketDetail -> ticketDetail.getSeatCodeSnapshot())
            .toList();

        boolean canViewFullPii = canCancelPendingPayment(transactionId, cancelToken, accessToken);
        String passengerName = canViewFullPii
                ? primaryDetail.getFullName()
                : PiiMaskingUtility.maskFullName(primaryDetail.getFullName());
        String passengerPhone = canViewFullPii
                ? primaryDetail.getPhone()
                : PiiMaskingUtility.maskPhone(primaryDetail.getPhone());

        String paymentStatus = paymentRepository.findStatusByTransactionId(transactionId)
                .orElse(payment.getStatus());

        return new BookingPaymentPageResponse(
            ticket.getTicketCode(),
            payment.getTransactionId(),
            payment.getAmount(),
            sepayBankAccount,
            sepayBankName,
            primaryDetail.getExpiredAt(),
            paymentStatus,
            passengerName,
            passengerPhone,
            seatCodes,
            ticket.getTripId()
        );
    }

    @Transactional
    @Override
    /**
     * Executes the expire pending payment if overdue operation.
     *
     * @param transactionId the value supplied for this operation
     */
    public void expirePendingPaymentIfOverdue(String transactionId) {
        passengerPendingPaymentService.expireIfOverdue(transactionId);
    }

    @Transactional
    @Override
    /**
     * Cancels the pending payment by user.
     *
     * @param transactionId the value supplied for this operation
     */
    public void cancelPendingPaymentByUser(String transactionId) {
        passengerPendingPaymentService.cancelByUser(transactionId);
    }

    @Override
    /**
     * Executes the can cancel pending payment operation.
     *
     * @param transactionId the value supplied for this operation
     * @param cancelToken the value supplied for this operation
     * @param accessToken the value supplied for this operation
     *
     * @return the operation result
     */
    public boolean canCancelPendingPayment(String transactionId, String cancelToken, String accessToken) {
        return passengerPendingPaymentService.canCancelByUser(transactionId, cancelToken, accessToken);
    }

    @Override
    /**
     * Executes the subscribe payment status operation.
     *
     * @param transactionId the value supplied for this operation
     *
     * @return the operation result
     */
    public SseEmitter subscribePaymentStatus(String transactionId) {
        Payment payment = paymentService.getPaymentByTransactionId(transactionId);
        SseEmitter emitter = paymentSseService.createConnection(transactionId);
        paymentSseService.sendStatusUpdate(transactionId, payment.getStatus());
        return emitter;
    }

    private record CoreCalculationResult(
        List<Integer> lockedSeatIds,
        BigDecimal basePrice,
        BigDecimal baseSurcharge,
        BigDecimal totalRawPrice,
        BigDecimal discountAmount,
        BigDecimal totalFinalPrice,
        CoachStop pickupStop,
        CoachStop dropoffStop,
        Voucher voucherToBeUsed,
        Map<Integer, TripSeat> tripSeatMap
    ) {}
    
    private CoreCalculationResult performCorePriceCalculation(
        Integer tripId, String holdToken, Integer pickupStopId, 
        Integer dropoffStopId, Integer voucherId, String accessToken
    ) {
        Trip trip = assertTripBookable(tripId);
        Integer routeId = trip.getRouteId();

        List<Integer> lockedSeatIds = seatHoldService.getTripSeatIdsByToken(holdToken);
        Map<Integer, TripSeat> tripSeatMap = validateAndFetchSeats(tripId, lockedSeatIds);
        BigDecimal basePrice = tripSeatMap.values().iterator().next().getPrice();

        RouteStopContext routeContext = calculateRouteSurcharge(routeId, pickupStopId, dropoffStopId);

        BigDecimal totalRawPrice = basePrice.add(routeContext.surcharge)
            .multiply(BigDecimal.valueOf(lockedSeatIds.size()));

        DiscountContext discountContext = calculateDiscount(voucherId, totalRawPrice, accessToken);
        
        BigDecimal finalPrice = totalRawPrice.subtract(discountContext.discountAmount).setScale(0, RoundingMode.HALF_UP);

        return new CoreCalculationResult(
            lockedSeatIds, 
            basePrice, 
            routeContext.surcharge, 
            totalRawPrice, 
            discountContext.discountAmount, 
            finalPrice.max(BigDecimal.ZERO), 
            routeContext.pickupStop, 
            routeContext.dropoffStop, 
            discountContext.voucher,
            tripSeatMap
        );
    }

    private Map<Integer, TripSeat> validateAndFetchSeats(Integer tripId, List<Integer> tripSeatIdsBooking) {
        if (tripSeatIdsBooking == null || tripSeatIdsBooking.isEmpty()) {
            throw new BusinessRuleException("Phiên giữ ghế đã hết hạn hoặc không hợp lệ. Vui lòng chọn lại!");
        }
        List<TripSeat> bookedSeats = tripSeatRepo.findByTripIdAndTripSeatIdInWithSeat(tripId, tripSeatIdsBooking);
        if (bookedSeats.size() != tripSeatIdsBooking.size()) {
            throw new BusinessRuleException("Có ghế ngồi không hợp lệ hoặc đã bị thay đổi. Vui lòng chọn lại!");
        }
        if (!bookedSeats.stream().allMatch(seat -> TripSeatStatus.AVAILABLE.equals(seat.getStatus()))) {
            throw new BusinessRuleException("Có ghế ngồi đã được đặt bởi người khác. Vui lòng chọn lại!");
        }
        if (bookedSeats.get(0).getPrice() == null) {
            throw new ResourceNotFoundException("Không tìm thấy thông tin giá của ghế này!");
        }
        return bookedSeats.stream().collect(Collectors.toMap(tripSeat -> tripSeat.getTripSeatId(), Function.identity()));
    }

    private record RouteStopContext(CoachStop pickupStop, CoachStop dropoffStop, BigDecimal surcharge) {}

    private RouteStopContext calculateRouteSurcharge(Integer routeId, Integer pickupStopId, Integer dropoffStopId) {
        if (pickupStopId == null || dropoffStopId == null) {
            return new RouteStopContext(null, null, BigDecimal.ZERO);
        }
        if (pickupStopId.equals(dropoffStopId)) {
            throw new BusinessRuleException("Điểm đón và điểm trả không được trùng nhau!");
        }
        
        RouteStop pickup = routeStopRepo.findByRouteIdAndStopPointId(routeId, pickupStopId)
            .orElseThrow(() -> new BusinessRuleException("Điểm đón không hợp lệ!"));
        RouteStop dropoff = routeStopRepo.findByRouteIdAndStopPointId(routeId, dropoffStopId)
            .orElseThrow(() -> new BusinessRuleException("Điểm trả không hợp lệ!"));

        if (pickup.getStopOrder() >= dropoff.getStopOrder()) {
            throw new BusinessRuleException("Lộ trình không hợp lệ: Điểm đón phải nằm trước điểm trả!");
        }

        BigDecimal surcharge = BigDecimal.ZERO;
        if (pickup.getCoachStop().getSurcharge() != null) surcharge = surcharge.add(pickup.getCoachStop().getSurcharge());
        if (dropoff.getCoachStop().getSurcharge() != null) surcharge = surcharge.add(dropoff.getCoachStop().getSurcharge());

        return new RouteStopContext(pickup.getCoachStop(), dropoff.getCoachStop(), surcharge);
    }

    private record DiscountContext(Voucher voucher, BigDecimal discountAmount) {}

    private DiscountContext calculateDiscount(Integer voucherId, BigDecimal totalRawPrice, String accessToken) {
        if (voucherId == null) return new DiscountContext(null, BigDecimal.ZERO);

        Voucher voucher = voucherService.getEligibleVoucher(voucherId, totalRawPrice);
        if (voucher == null) {
            throw new BusinessRuleException("Mã giảm giá không hợp lệ hoặc không đáp ứng điều kiện!");
        }

        Integer accountId = jwtService.extractAccountId(accessToken);
        if (accountId != null && ticketRepo.getUsedVoucherIdsByAccountId(accountId, PassengerTicketStatus.CANCELLED).contains(voucherId)) {
            throw new BusinessRuleException("Không thành công, voucher này đã được sử dụng!");
        }

        BigDecimal discount = voucher.getDiscountType().equals(VoucherType.FIXED.getValue()) 
            ? voucher.getDiscountValue() 
            : totalRawPrice.multiply(voucher.getDiscountValue()).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
        
        if (voucher.getMaxDiscountValue() != null) discount = discount.min(voucher.getMaxDiscountValue());
        
        return new DiscountContext(voucher, discount.min(totalRawPrice));
    }

    private void saveTicketDetailsAndChildrenInBatch(Integer ticketId, List<PassengerDTO> passengers, CoreCalculationResult coreResult) {
        LocalDateTime expiredAt = LocalDateTime.now().plusSeconds(PAYMENT_HOLD_TTL_SECONDS); 
        BigDecimal pricePerSeat = coreResult.basePrice().add(coreResult.baseSurcharge());

        List<PassengerTicketDetail> detailsToSave = passengers.stream().map(p -> 
            PassengerTicketDetail.builder()
                .passengerTicketId(ticketId)
                .tripSeatId(p.tripSeatId())
                .seatCodeSnapshot(coreResult.tripSeatMap().get(p.tripSeatId()).getSeat().getSeatCode())
                .fullName(p.fullname().trim())
                .phone(p.phone().trim())
                .email(p.email().trim())
                .price(pricePerSeat)
                .status(PassengerTicketDetailStatus.PENDING.name())
                .expiredAt(expiredAt)
                .build()
        ).toList();

        List<PassengerTicketDetail> savedDetails = ticketDetailRepo.saveAll(detailsToSave);

        List<AccompaniedChild> childrenToSave = new ArrayList<>();
        for (PassengerDTO dto : passengers) {
            if (dto.accompaniedChild() != null) {
                savedDetails.stream()
                    .filter(sd -> sd.getTripSeatId() == dto.tripSeatId())
                    .findFirst()
                    .ifPresent(sd -> {
                        childrenToSave.add(AccompaniedChild.builder()
                            .ticketDetailId(sd.getTicketDetailId())
                            .fullname(dto.accompaniedChild().fullname().trim())
                            .birthYear(dto.accompaniedChild().birthYear())
                            .build());
                    });
            }
        }
        
        if (!childrenToSave.isEmpty()) {
            accompaniedChildRepo.saveAll(childrenToSave); 
        }
    }

    private PassengerTicket saveMasterTicket(Integer tripId, BookingConfirmRequest request, CoreCalculationResult coreResult, Integer customerId, String voucherCodeSnapshot) {
        LocalDateTime refundPolicyDepartureTime = tripRepo.findById(tripId)
            .map(trip -> trip.getDepartureTime())
            .orElseThrow(() -> new BusinessRuleException("Không tìm thấy chuyến xe."));

        return ticketRepo.save(PassengerTicket.builder()
            .customerId(customerId)
            .tripId(tripId)
            .ticketCode(ticketCodeGenerator.generatePassengerTicketCode())
            .totalPrice(coreResult.totalFinalPrice())
            .voucherId(request.voucherId())
            .pickupStopId(coreResult.pickupStop().getStopPointId())
            .dropoffStopId(coreResult.dropoffStop().getStopPointId())
            .pickupStopName(coreResult.pickupStop().getStopPointName())
            .dropoffStopName(coreResult.dropoffStop().getStopPointName())
            .voucherCodeSnapshot(voucherCodeSnapshot)
            .refundPolicyDepartureTime(refundPolicyDepartureTime)
            .status(PassengerTicketStatus.PENDING)
            .build());
    }

    private void validateRequestSeatsMatchLockedSeats(BookingConfirmRequest request, List<Integer> lockedSeatIds) {
        Set<Integer> lockedSet = Set.copyOf(lockedSeatIds);
        Set<Integer> requestedSet = request.passengers().stream()
            .map(passengerDTO -> passengerDTO.tripSeatId()).collect(Collectors.toSet());
        if (requestedSet.size() != request.passengers().size() || !requestedSet.equals(lockedSet)) {
            throw new BusinessRuleException("Danh sách hành khách không khớp với ghế đang giữ!");
        }
    }

    private String processVoucherUsage(Voucher voucher) {
        if (voucher != null) {
            if (voucherService.incrementUsedCountIfAvailable(voucher.getVoucherId()) == 0) {
                throw new BusinessRuleException("Voucher vừa hết lượt sử dụng! Vui lòng chọn lại.");
            }
            return voucher.getVoucherCode();
        }
        return null;
    }

    private Integer resolveCustomerId(String accessToken) {
        if (accessToken != null && !accessToken.isBlank()) {
            Integer accountId = jwtService.extractAccountId(accessToken);
            if (accountId != null) {
                return customerRepo.findByAccountId(accountId)
                    .map(customer -> customer.getCustomerId()).orElse(null);
            }
        }
        return null;
    }

    private CustomerProfileDTO resolveCustomerProfile(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return null;
        }

        Integer accountId = jwtService.extractAccountId(accessToken);
        if (accountId == null) {
            return null;
        }

        Customer customer = customerRepo.findByAccountId(accountId).orElse(null);
        Account account = accountRepo.findById(accountId).orElse(null);
        if (customer == null && account == null) {
            return null;
        }

        String fullname = customer != null ? customer.getCustomerName() : "User";
        String phone = resolveProfilePhone(customer, account);
        if (phone == null) {
            return null;
        }

        String email = customer != null ? customer.getEmail() : null;
        return new CustomerProfileDTO(fullname, phone, email);
    }

    private String resolveProfilePhone(Customer customer, Account account) {
        if (customer != null && customer.getPhone() != null && !customer.getPhone().isBlank()) {
            return PhoneNumberUtility.normalizeToLocalFormat(customer.getPhone());
        }

        if (account != null
                && "firebase".equals(account.getAuthProvider())
                && account.getUsername() != null
                && account.getUsername().matches(BookingValidationPatterns.PHONE)) {
            return account.getUsername();
        }

        return null;
    }

    private void validatePassengerPhoneVerification(List<PassengerDTO> passengers) {
        java.util.Set<String> checkedPhones = new java.util.HashSet<>();

        for (PassengerDTO passenger : passengers) {
            String phone = PhoneNumberUtility.normalizeToLocalFormat(passenger.phone());
            if (!checkedPhones.add(phone)) {
                continue;
            }

            if (passengerPhoneVerificationService.isPhoneKnown(phone)) {
                continue;
            }

            passengerPhoneVerificationService.verifyFirebasePhoneToken(phone, passenger.firebaseIdToken());
        }
    }

    private List<CoachStopDropdownDTO> getStopPointDropdown(List<RouteStop> stopPointList, String province){
        return stopPointList.stream().filter(stop -> stop.getCoachStop().getCity().trim().equalsIgnoreCase(province)).map(stop -> new CoachStopDropdownDTO(
            stop.getCoachStop().getStopPointId(), stop.getCoachStop().getStopPointName(), stop.getMinutesFromStart()
        )).toList();
    }

    private List<VoucherDTO> getEligibleVouchers(String accessToken) {
        Integer accountId = jwtService.extractAccountId(accessToken);
        List<VoucherDTO> eligibleVouchers = new ArrayList<>();
        if(accountId != null) {
            List<VoucherDTO> availableVouchers = voucherService.getEligibleVouchers();
            Set<Integer> usedVoucherIds = ticketRepo.getUsedVoucherIdsByAccountId(accountId, PassengerTicketStatus.CANCELLED);
            eligibleVouchers = availableVouchers.stream().filter(voucher -> !usedVoucherIds.contains(voucher.voucherId())).toList();
        }
        return eligibleVouchers;
    }

    private void validatePassengerChildBirthYears(List<PassengerDTO> passengers) {
        int currentYear = Year.now().getValue();
        for (PassengerDTO p : passengers) {
            if (p.accompaniedChild() != null && p.accompaniedChild().birthYear() > currentYear) {
                throw new BusinessRuleException("Năm sinh của bé không thể > năm hiện tại!");
            }
        }
    }

    /**
     * Customer booking is allowed only for SCHEDULED trips that have not departed yet.
     * Aligns with customer trip search filters so deep-linked tripIds cannot bypass the list.
     */
    private Trip assertTripBookable(Integer tripId) {
        Trip trip = tripRepo.findById(tripId)
            .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chuyến xe có ID là: " + tripId));

        if (!"SCHEDULED".equals(trip.getStatus())) {
            throw new BusinessRuleException("Chuyến xe này không còn mở để đặt vé!");
        }
        if (trip.getDepartureTime() == null || trip.getDepartureTime().isBefore(LocalDateTime.now())) {
            throw new BusinessRuleException("Chuyến xe đã khởi hành hoặc đã quá giờ đặt vé!");
        }
        return trip;
    }

}
