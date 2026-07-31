package com.ralsei.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import com.ralsei.exception.BusinessRuleException;
import com.ralsei.model.CargoTicket;
import com.ralsei.model.Payment;
import com.ralsei.model.Refund;
import com.ralsei.model.Staff;
import com.ralsei.model.TicketAgency;
import com.ralsei.repository.CargoTicketDetailRepository;
import com.ralsei.repository.CargoTicketRepository;
import com.ralsei.repository.CargoTypePriceRepository;
import com.ralsei.repository.CoachStopRepository;
import com.ralsei.repository.CustomerRepository;
import com.ralsei.repository.PaymentRepository;
import com.ralsei.repository.RefundRepository;
import com.ralsei.repository.RouteRepository;
import com.ralsei.repository.StaffRepository;
import com.ralsei.repository.TicketAgencyRepository;
import com.ralsei.repository.TripRepository;
import com.ralsei.service.TransactionIdGenerator;
import com.ralsei.service.cargoticket.CargoTicketPaymentPolicy;
import com.ralsei.service.cargoticket.impl.CargoTicketPaymentPolicyImpl;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CargoTicketPaymentLifecycleTest {

    @Mock private CargoTicketRepository cargoTicketRepository;
    @Mock private CargoTicketDetailRepository cargoTicketDetailRepository;
    @Mock private TripRepository tripRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private CoachStopRepository coachStopRepository;
    @Mock private StaffRepository staffRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private CargoTypePriceRepository cargoTypePriceRepository;
    @Mock private TransactionIdGenerator transactionIdGenerator;
    @Mock private RouteRepository routeRepository;
    @Mock private TicketAgencyRepository ticketAgencyRepository;
    @Mock private RefundRepository refundRepository;

    @InjectMocks
    private CargoTicketServiceImpl cargoTicketService;

    private CargoTicketPaymentPolicy paymentPolicy;
    private Staff ticketStaff;
    private TicketAgency destinationAgency;

    @BeforeEach
    void setUp() {
        paymentPolicy = new CargoTicketPaymentPolicyImpl(paymentRepository, refundRepository);
        ReflectionTestUtils.setField(cargoTicketService, "cargoTicketPaymentPolicy", paymentPolicy);

        ticketStaff = new Staff();
        ticketStaff.setStaffId(3);
        ticketStaff.setStaffPosition("TICKET_STAFF");
        ticketStaff.setTicketAgencyId(2);
        ticketStaff.setActive(true);

        destinationAgency = TicketAgency.builder()
                .ticketAgencyId(2)
                .stopPointId(8)
                .ticketAgencyName("Destination")
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("SENDER cash create helper marks payment COMPLETED")
    void senderCashCreateCompletesPayment() {
        Payment payment = Payment.builder()
                .paymentId(1)
                .paymentMethod("CASH")
                .status("PENDING")
                .amount(BigDecimal.TEN)
                .refundAmount(BigDecimal.ZERO)
                .build();

        paymentPolicy.completeCashIfApplicableOnCreate(payment, "SENDER");

        assertEquals("COMPLETED", payment.getStatus());
        verify(paymentRepository).save(payment);
    }

    @Test
    @DisplayName("SENDER bank create helper leaves payment PENDING")
    void senderBankCreateKeepsPending() {
        Payment payment = Payment.builder()
                .paymentId(1)
                .paymentMethod("BANK_TRANSFER")
                .status("PENDING")
                .amount(BigDecimal.TEN)
                .refundAmount(BigDecimal.ZERO)
                .build();

        paymentPolicy.completeCashIfApplicableOnCreate(payment, "SENDER");

        assertEquals("PENDING", payment.getStatus());
        verify(paymentRepository, never()).save(payment);
    }

    @Test
    @DisplayName("load gate rejects unpaid SENDER")
    void loadRejectsUnpaidSender() {
        CargoTicket ticket = CargoTicket.builder()
                .cargoTicketId(10)
                .feePayer("SENDER")
                .status("RECEIVED")
                .build();
        Payment payment = Payment.builder()
                .paymentId(1)
                .paymentMethod("BANK_TRANSFER")
                .status("PENDING")
                .amount(BigDecimal.TEN)
                .refundAmount(BigDecimal.ZERO)
                .build();
        when(paymentRepository.findByCargoTicket_CargoTicketId(10)).thenReturn(Optional.of(payment));

        assertThrows(BusinessRuleException.class,
                () -> paymentPolicy.requireSenderPaidBeforeLoad(ticket));
    }

    @Test
    @DisplayName("confirmReceived with RECEIVER cash completes payment then delivers")
    void confirmReceiverCashCompletesAndDelivers() {
        CargoTicket ticket = CargoTicket.builder()
                .cargoTicketId(99)
                .dropoffStopId(8)
                .feePayer("RECEIVER")
                .status("ARRIVED")
                .build();
        Payment payment = Payment.builder()
                .paymentId(7)
                .paymentMethod("CASH")
                .status("PENDING")
                .amount(BigDecimal.valueOf(50000))
                .refundAmount(BigDecimal.ZERO)
                .build();

        when(staffRepository.findByAccountId(123)).thenReturn(Optional.of(ticketStaff));
        when(ticketAgencyRepository.findByTicketAgencyIdAndIsActiveTrue(2))
                .thenReturn(Optional.of(destinationAgency));
        when(cargoTicketRepository.findByCargoTicketIdAndStatusNot(99, "ABANDONED"))
                .thenReturn(Optional.of(ticket));
        when(paymentRepository.findByCargoTicket_CargoTicketId(99)).thenReturn(Optional.of(payment));

        cargoTicketService.confirmReceived(99, 123, null);

        assertEquals("COMPLETED", payment.getStatus());
        assertEquals("DELIVERED", ticket.getStatus());
        verify(cargoTicketRepository).save(ticket);
    }

    @Test
    @DisplayName("confirmReceived with RECEIVER unpaid bank is rejected")
    void confirmReceiverUnpaidBankRejected() {
        CargoTicket ticket = CargoTicket.builder()
                .cargoTicketId(99)
                .dropoffStopId(8)
                .feePayer("RECEIVER")
                .status("ARRIVED")
                .build();
        Payment payment = Payment.builder()
                .paymentId(7)
                .paymentMethod("BANK_TRANSFER")
                .status("PENDING")
                .amount(BigDecimal.valueOf(50000))
                .refundAmount(BigDecimal.ZERO)
                .build();

        when(staffRepository.findByAccountId(123)).thenReturn(Optional.of(ticketStaff));
        when(ticketAgencyRepository.findByTicketAgencyIdAndIsActiveTrue(2))
                .thenReturn(Optional.of(destinationAgency));
        when(cargoTicketRepository.findByCargoTicketIdAndStatusNot(99, "ABANDONED"))
                .thenReturn(Optional.of(ticket));
        when(paymentRepository.findByCargoTicket_CargoTicketId(99)).thenReturn(Optional.of(payment));

        assertThrows(BusinessRuleException.class, () -> cargoTicketService.confirmReceived(99, 123, null));
        assertEquals("ARRIVED", ticket.getStatus());
    }

    @Test
    @DisplayName("cancel PENDING payment marks FAILED")
    void cancelPendingFailsPayment() {
        CargoTicket ticket = CargoTicket.builder()
                .cargoTicketId(55)
                .ticketCode("CG-TEST")
                .status("RECEIVED")
                .build();
        Payment payment = Payment.builder()
                .paymentId(3)
                .status("PENDING")
                .paymentMethod("CASH")
                .amount(BigDecimal.TEN)
                .refundAmount(BigDecimal.ZERO)
                .build();

        when(cargoTicketRepository.findByCargoTicketIdAndStatusNot(55, "ABANDONED"))
                .thenReturn(Optional.of(ticket));
        when(paymentRepository.findByCargoTicket_CargoTicketId(55)).thenReturn(Optional.of(payment));

        cargoTicketService.disable(55);

        assertEquals("CANCELLED", ticket.getStatus());
        assertEquals("FAILED", payment.getStatus());
        verify(refundRepository, never()).save(any());
    }

    @Test
    @DisplayName("cancel COMPLETED payment creates PENDING refund request")
    void cancelCompletedCreatesRefundRequest() {
        CargoTicket ticket = CargoTicket.builder()
                .cargoTicketId(55)
                .ticketCode("CG-TEST")
                .status("RECEIVED")
                .build();
        Payment payment = Payment.builder()
                .paymentId(3)
                .status("COMPLETED")
                .paymentMethod("CASH")
                .amount(BigDecimal.valueOf(120000))
                .refundAmount(BigDecimal.ZERO)
                .build();

        when(cargoTicketRepository.findByCargoTicketIdAndStatusNot(55, "ABANDONED"))
                .thenReturn(Optional.of(ticket));
        when(paymentRepository.findByCargoTicket_CargoTicketId(55)).thenReturn(Optional.of(payment));
        when(refundRepository.existsByPaymentIdAndStatusIn(eq(3), any())).thenReturn(false);

        cargoTicketService.disable(55);

        assertEquals("CANCELLED", ticket.getStatus());
        ArgumentCaptor<Refund> refundCaptor = ArgumentCaptor.forClass(Refund.class);
        verify(refundRepository).save(refundCaptor.capture());
        assertEquals("PENDING", refundCaptor.getValue().getStatus());
        assertEquals(BigDecimal.valueOf(120000), refundCaptor.getValue().getAmount());
        assertEquals(BigDecimal.valueOf(120000), payment.getRefundAmount());
    }

    @Test
    @DisplayName("paid order rejects money field changes")
    void paidOrderRejectsMoneyChanges() {
        Payment payment = Payment.builder()
                .paymentId(1)
                .status("COMPLETED")
                .paymentMethod("CASH")
                .amount(BigDecimal.TEN)
                .refundAmount(BigDecimal.ZERO)
                .build();
        assertThrows(BusinessRuleException.class,
                () -> paymentPolicy.rejectMoneyChangesWhenPaid(payment));
    }
}
