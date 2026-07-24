package com.ralsei.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.ralsei.dto.request.passengerbooking.BookingConfirmRequest;
import com.ralsei.dto.request.passengerbooking.PriceCalculationRequest;
import com.ralsei.dto.request.passengerbooking.SeatLockRequest;
import com.ralsei.dto.response.passengerbooking.BookingConfirmResponse;
import com.ralsei.dto.response.passengerbooking.BookingPaymentPageResponse;
import com.ralsei.dto.response.passengerbooking.CheckPhoneResponse;
import com.ralsei.dto.response.passengerbooking.PriceCalculationResponse;
import com.ralsei.dto.response.passengerbooking.SeatLockResponse;
import com.ralsei.dto.response.passengerbooking.Step2InitResponse;
import com.ralsei.dto.response.passengerbooking.TripSeatResponse;
import com.ralsei.exception.BusinessRuleException;
import com.ralsei.service.passengerbooking.PassengerBookingService;
import com.ralsei.util.validation.BookingValidationPatterns;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Booking", description = "Customer passenger booking and seat lock")
@RestController
@RequestMapping("/api/v1/bookings")
@PreAuthorize("isAnonymous() or hasRole('CUSTOMER')")
@RequiredArgsConstructor
@Validated
/**
 * Handles HTTP requests for passenger booking operations.
 */
public class PassengerBookingController {
    
    private final PassengerBookingService bookingService;

    @GetMapping("/trips/{tripId}/seats")
    public ResponseEntity<List<TripSeatResponse>> getSeatMap(@PathVariable @Min(value = 1, message = "ID của Chuyến phải lớn hơn 0.") Integer tripId) {
        return ResponseEntity.ok(bookingService.getSeatMap(tripId));
    }

    @PostMapping("/trips/{tripId}/seats/lock")
    public ResponseEntity<SeatLockResponse> lockSeats(
        @PathVariable @Min(value = 1, message = "ID của Chuyến phải lớn hơn 0.") Integer tripId,
        @Valid @RequestBody SeatLockRequest request,
        @RequestHeader("X-Booking-Session") String holdToken
    ) {
        return ResponseEntity.ok(bookingService.lockSeats(tripId, request, holdToken));
    }

    @PostMapping("/trips/{tripId}/seats/release")
    public ResponseEntity<Boolean> releaseSeats(
        @PathVariable @Min(value = 1, message = "ID chuyến xe phải lớn hơn 0.") Integer tripId,
        @Valid @RequestBody SeatLockRequest request, 
        @RequestHeader("X-Booking-Session") String holdToken
    ) {
        return ResponseEntity.ok(bookingService.releaseSeats(request.tripSeatIds(), holdToken)); 
    }

    @PostMapping(
        path = "/trips/{tripId:\\d+}/seats/release/beacon",
        consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void releaseSeatsByBeacon(
        @PathVariable @Min(value = 1, message = "ID chuyến xe phải lớn hơn 0.") Integer tripId,
        @RequestParam("session") String holdToken
    ) {
        bookingService.releaseSeatsByBecon(holdToken);
    }

    @GetMapping("/check-phone")
    public ResponseEntity<CheckPhoneResponse> checkPhone(
            @RequestParam
            @Pattern(regexp = BookingValidationPatterns.PHONE, message = "Số điện thoại không hợp lệ!")
            String phone) {
        return ResponseEntity.ok(bookingService.checkPhone(phone));
    }

    @GetMapping("/trips/{tripId}/step2-init-data")
    public ResponseEntity<Step2InitResponse> getStep2InitData(
        @PathVariable @Min(value = 1, message = "ID của Chuyến phải lớn hơn 0.") Integer tripId,
        @RequestHeader("X-Booking-Session") String holdToken,
        @RequestHeader(value ="Authorization", required=false) String accessToken
    ) {
        return ResponseEntity.ok(bookingService.getStep2InitData(tripId, holdToken, accessToken));
    }

    @PostMapping("/trips/{tripId}/calculate-price")
    public ResponseEntity<PriceCalculationResponse> calculatePrice(
        @PathVariable @Min(value = 1, message = "ID của Chuyến phải lớn hơn 0.") Integer tripId,
        @RequestBody PriceCalculationRequest request,
        @RequestHeader("X-Booking-Session") String holdToken,
        @RequestHeader(value ="Authorization", required=false) String accessToken
    ) {
        PriceCalculationRequest fullRequest = new PriceCalculationRequest(
            holdToken,
            request.pickupStopId(),
            request.dropoffStopId(),
            request.voucherId()
        );
        return ResponseEntity.ok(bookingService.calculatePrice(tripId, fullRequest, accessToken));
    }

    @PostMapping("/trips/{tripId}/confirm")
    public ResponseEntity<BookingConfirmResponse> confirmBooking(
        @PathVariable @Min(value = 1, message = "ID của Chuyến phải lớn hơn 0.") Integer tripId,
        @Valid @RequestBody BookingConfirmRequest request,
        @RequestHeader("X-Booking-Session") String holdToken,
        @RequestHeader(value ="Authorization", required=false) String accessToken
    ) {
        return ResponseEntity.ok(bookingService.confirmBooking(tripId, request, holdToken, accessToken));
    }

    @GetMapping("/payments/{transactionId}")
    public ResponseEntity<BookingPaymentPageResponse> getPaymentPage(
        @PathVariable String transactionId,
        @RequestHeader(value = "X-Cancel-Token", required = false) String cancelToken,
        @RequestHeader(value = "Authorization", required = false) String accessToken
    ) {
        bookingService.expirePendingPaymentIfOverdue(transactionId);
        return ResponseEntity.ok(bookingService.getPaymentPage(transactionId, cancelToken, accessToken));
    }

    @GetMapping(value = "/payments/{transactionId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    /**
     * Executes the stream payment status operation.
     *
     * @param transactionId the value supplied for this operation
     *
     * @return the operation result
     */
    public SseEmitter streamPaymentStatus(@PathVariable String transactionId) {
        bookingService.expirePendingPaymentIfOverdue(transactionId);
        return bookingService.subscribePaymentStatus(transactionId);
    }

    @PostMapping("/payments/{transactionId}/expire")
    public ResponseEntity<BookingPaymentPageResponse> expirePaymentIfOverdue(
            @PathVariable String transactionId,
            @RequestHeader(value = "X-Cancel-Token", required = false) String cancelToken,
            @RequestHeader(value = "Authorization", required = false) String accessToken) {
        bookingService.expirePendingPaymentIfOverdue(transactionId);
        return ResponseEntity.ok(bookingService.getPaymentPage(transactionId, cancelToken, accessToken));
    }

    @PostMapping("/payments/{transactionId}/cancel")
    public ResponseEntity<BookingPaymentPageResponse> cancelPendingPayment(
            @PathVariable String transactionId,
            @RequestHeader(value = "X-Cancel-Token", required = false) String cancelToken,
            @RequestHeader(value = "Authorization", required = false) String accessToken) {

        if (!bookingService.canCancelPendingPayment(transactionId, cancelToken, accessToken)) {
            throw new BusinessRuleException("Bạn không có quyền hủy giao dịch này!");
        }

        bookingService.cancelPendingPaymentByUser(transactionId);
        return ResponseEntity.ok(bookingService.getPaymentPage(transactionId, cancelToken, accessToken));
    }
}
