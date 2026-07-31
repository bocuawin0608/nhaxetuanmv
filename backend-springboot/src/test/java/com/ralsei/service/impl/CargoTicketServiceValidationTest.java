package com.ralsei.service.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
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

import com.ralsei.dto.request.cargoticket.CargoTicketRequest;
import com.ralsei.dto.request.cargoticket.CargoTripAssignRequest;
import com.ralsei.dto.response.cargoticket.CargoAssignableBoardResponse;
import com.ralsei.dto.response.cargoticket.CargoTicketResponse;
import com.ralsei.dto.response.cargoticket.CargoTripAssignResponse;
import com.ralsei.exception.BusinessRuleException;
import com.ralsei.model.CargoTicket;
import com.ralsei.model.Payment;
import com.ralsei.model.Staff;
import com.ralsei.model.TicketAgency;
import com.ralsei.model.Trip;
import com.ralsei.repository.CargoTicketDetailRepository;
import com.ralsei.repository.CargoTicketRepository;
import com.ralsei.repository.CoachStopRepository;
import com.ralsei.repository.CustomerRepository;
import com.ralsei.repository.PaymentRepository;
import com.ralsei.repository.RefundRepository;
import com.ralsei.repository.RouteRepository;
import com.ralsei.repository.RouteStopRepository;
import com.ralsei.repository.StaffRepository;
import com.ralsei.repository.TicketAgencyRepository;
import com.ralsei.repository.TripRepository;
import com.ralsei.service.TransactionIdGenerator;
import com.ralsei.service.cargoticket.impl.CargoTicketPaymentPolicyImpl;

import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CargoTicketServiceValidationTest {

        @Mock
        private CoachStopRepository coachStopRepository;
        @Mock
        private TripRepository tripRepository;
        @Mock
        private CustomerRepository customerRepository;
        @Mock
        private StaffRepository staffRepository;
        @Mock
        private TicketAgencyRepository ticketAgencyRepository;
        @Mock
        private CargoTicketRepository cargoTicketRepository;
        @Mock
        private CargoTicketDetailRepository cargoTicketDetailRepository;
        @Mock
        private PaymentRepository paymentRepository;
        @Mock
        private RefundRepository refundRepository;
        @Mock
        private TransactionIdGenerator transactionIdGenerator;
        @Mock
        private RouteRepository routeRepository;
        @Mock
        private RouteStopRepository routeStopRepository;

        @InjectMocks
        private CargoTicketServiceImpl cargoTicketService;

        private CargoTicketRequest request;
        private Staff currentStaff;

        @BeforeEach
        void setUp() {
                CargoTicketPaymentPolicyImpl paymentPolicy = new CargoTicketPaymentPolicyImpl(paymentRepository,
                                refundRepository);
                ReflectionTestUtils.setField(cargoTicketService, "cargoTicketPaymentPolicy", paymentPolicy);

                request = new CargoTicketRequest();
                request.setPickupStopId(1);
                request.setDropoffStopId(8);
                request.setSenderName("TestSender");
                request.setSenderPhone("0900000001");
                request.setReceiverName("TestReceiver");
                request.setReceiverPhone("0900000002");
                request.setTotalPrice(BigDecimal.valueOf(100000));
                request.setFeePayer("SENDER");
                request.setPaymentMethod("CASH");
                request.setCodAmount(BigDecimal.ZERO);

                Staff sellerStaff = new Staff();
                sellerStaff.setStaffId(3);
                request.setSoldBy(sellerStaff);

                currentStaff = new Staff();
                currentStaff.setStaffId(3);
                currentStaff.setStaffPosition("TICKET_STAFF");
                currentStaff.setTicketAgencyId(2);
                currentStaff.setActive(true);
        }

        @Test
        @DisplayName("TEST 1a (Validation): Rejects cargo with agency-less dropoffStopId")
        void testRejectsCargoAtAgencylessDropoff() {
                // stops exist
                when(coachStopRepository.existsById(1)).thenReturn(true);
                when(coachStopRepository.existsById(8)).thenReturn(true);
                // trip exists
                when(tripRepository.existsById(anyInt())).thenReturn(true);
                // staff checks
                when(staffRepository.existsById(anyInt())).thenReturn(true);
                when(staffRepository.existsByStaffIdAndIsActiveTrueAndStaffPosition(anyInt(), anyString()))
                                .thenReturn(true);
                when(staffRepository.existsByStaffIdAndIsActiveTrueAndStaffPositionIn(anyInt(), anyList()))
                                .thenReturn(true);
                // trip eligibility
                when(tripRepository.isEligibleForAgencyCargo(anyInt(), anyInt(), anyInt(), anyInt()))
                                .thenReturn(true);

                // pickup stop has agency, dropoff does NOT
                when(ticketAgencyRepository.existsByStopPointIdAndIsActiveTrue(1)).thenReturn(true);
                when(ticketAgencyRepository.existsByStopPointIdAndIsActiveTrue(8)).thenReturn(false);

                var ex = assertThrows(BusinessRuleException.class, () -> {
                        cargoTicketService.newCargoTicketValidation_onlyForTest(request, currentStaff);
                });
                assertEquals("Điểm trả hàng không có văn phòng vé đang hoạt động.", ex.getMessage());
        }

        @Test
        @DisplayName("TEST 1b (Validation): Rejects cargo with agency-less pickupStopId")
        void testRejectsCargoAtAgencylessPickup() {
                when(coachStopRepository.existsById(1)).thenReturn(true);
                when(coachStopRepository.existsById(8)).thenReturn(true);
                when(tripRepository.existsById(anyInt())).thenReturn(true);
                when(staffRepository.existsById(anyInt())).thenReturn(true);
                when(staffRepository.existsByStaffIdAndIsActiveTrueAndStaffPosition(anyInt(), anyString()))
                                .thenReturn(true);
                when(staffRepository.existsByStaffIdAndIsActiveTrueAndStaffPositionIn(anyInt(), anyList()))
                                .thenReturn(true);
                when(tripRepository.isEligibleForAgencyCargo(anyInt(), anyInt(), anyInt(), anyInt()))
                                .thenReturn(true);

                // pickup stop has NO agency, dropoff does
                when(ticketAgencyRepository.existsByStopPointIdAndIsActiveTrue(1)).thenReturn(false);
                when(ticketAgencyRepository.existsByStopPointIdAndIsActiveTrue(8)).thenReturn(true);

                var ex = assertThrows(BusinessRuleException.class, () -> {
                        cargoTicketService.newCargoTicketValidation_onlyForTest(request, currentStaff);
                });
                assertEquals("Điểm nhận hàng không có văn phòng vé đang hoạt động.", ex.getMessage());
        }

        @Test
        @DisplayName("TEST 1c (Validation): Accepts cargo with valid agencies at both stops")
        void testAcceptsCargoWithValidAgencies() {
                when(coachStopRepository.existsById(1)).thenReturn(true);
                when(coachStopRepository.existsById(8)).thenReturn(true);
                when(tripRepository.existsById(anyInt())).thenReturn(true);
                when(staffRepository.existsById(anyInt())).thenReturn(true);
                when(staffRepository.existsByStaffIdAndIsActiveTrueAndStaffPosition(anyInt(), anyString()))
                                .thenReturn(true);
                when(staffRepository.existsByStaffIdAndIsActiveTrueAndStaffPositionIn(anyInt(), anyList()))
                                .thenReturn(true);
                when(tripRepository.isEligibleForAgencyCargo(anyInt(), anyInt(), anyInt(), anyInt()))
                                .thenReturn(true);

                // BOTH stops have active agency
                when(ticketAgencyRepository.existsByStopPointIdAndIsActiveTrue(1)).thenReturn(true);
                when(ticketAgencyRepository.existsByStopPointIdAndIsActiveTrue(8)).thenReturn(true);

                assertDoesNotThrow(() -> {
                        cargoTicketService.newCargoTicketValidation_onlyForTest(request, currentStaff);
                });
        }

        /**
         * Verifies that the create workflow rejects a stale selection as soon as its
         * trip changes from SCHEDULED to IN_PROGRESS.
         */
        @Test
        @DisplayName("Cargo creation is rejected after the selected trip starts")
        void rejectsCargoCreationAfterTripStarts() {
                request.setTripId(77);
                Trip startedTrip = Trip.builder()
                                .tripId(77)
                                .status("IN_PROGRESS")
                                .build();

                when(staffRepository.findByAccountId(123)).thenReturn(Optional.of(currentStaff));
                when(tripRepository.findById(77)).thenReturn(Optional.of(startedTrip));

                var exception = assertThrows(
                                BusinessRuleException.class,
                                () -> cargoTicketService.createCargoTicket(request, 123));

                assertEquals(
                                "Chuyến xe đã khởi hành hoặc không còn hoạt động, không thể nhận thêm hàng.",
                                exception.getMessage());
        }

        /**
         * Destination ticket staff confirms customer hand-off from ARRIVED only.
         */
        @Test
        @DisplayName("ARRIVED cargo can be confirmed as DELIVERED by destination ticket staff")
        void confirmsArrivedCargoAtDestinationAgency() {
                TicketAgency destinationAgency = TicketAgency.builder()
                                .ticketAgencyId(2)
                                .stopPointId(8)
                                .ticketAgencyName("Destination")
                                .isActive(true)
                                .build();
                CargoTicket ticket = CargoTicket.builder()
                                .cargoTicketId(99)
                                .dropoffStopId(8)
                                .feePayer("SENDER")
                                .status("ARRIVED")
                                .build();
                Payment payment = Payment.builder()
                                .paymentId(1)
                                .paymentMethod("CASH")
                                .status("COMPLETED")
                                .amount(BigDecimal.TEN)
                                .refundAmount(BigDecimal.ZERO)
                                .build();

                when(staffRepository.findByAccountId(123)).thenReturn(Optional.of(currentStaff));
                when(ticketAgencyRepository.findByTicketAgencyIdAndIsActiveTrue(2))
                                .thenReturn(Optional.of(destinationAgency));
                when(cargoTicketRepository.findByCargoTicketIdAndStatusNot(99, "ABANDONED"))
                                .thenReturn(Optional.of(ticket));
                when(paymentRepository.findByCargoTicket_CargoTicketId(99)).thenReturn(Optional.of(payment));

                cargoTicketService.confirmReceived(99, 123, null);

                assertEquals("DELIVERED", ticket.getStatus());
                assertSame(currentStaff, ticket.getDeliveredBy());
                verify(cargoTicketRepository).save(ticket);
        }

        @Test
        @DisplayName("Validation accepts cargo order with tripId deferred (null)")
        void acceptsCargoValidationWithoutTrip() {
                request.setTripId(null);
                when(coachStopRepository.existsById(1)).thenReturn(true);
                when(coachStopRepository.existsById(8)).thenReturn(true);
                when(staffRepository.existsById(anyInt())).thenReturn(true);
                when(staffRepository.existsByStaffIdAndIsActiveTrueAndStaffPosition(anyInt(), anyString()))
                                .thenReturn(true);
                when(ticketAgencyRepository.existsByStopPointIdAndIsActiveTrue(1)).thenReturn(true);
                when(ticketAgencyRepository.existsByStopPointIdAndIsActiveTrue(8)).thenReturn(true);

                assertDoesNotThrow(
                                () -> cargoTicketService.newCargoTicketValidation_onlyForTest(request, currentStaff));
                verify(tripRepository, never()).isEligibleForAgencyCargo(anyInt(), anyInt(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("Create succeeds with tripId null (deferred assignment)")
        void createsCargoTicketWithoutTrip() {
                request.setTripId(null);
                request.setPaymentMethod("CASH");

                TicketAgency agency = TicketAgency.builder()
                                .ticketAgencyId(2)
                                .stopPointId(1)
                                .ticketAgencyName("Origin")
                                .isActive(true)
                                .build();

                when(staffRepository.findByAccountId(123)).thenReturn(Optional.of(currentStaff));
                when(ticketAgencyRepository.findByTicketAgencyIdAndIsActiveTrue(2))
                                .thenReturn(Optional.of(agency));
                when(coachStopRepository.existsById(1)).thenReturn(true);
                when(coachStopRepository.existsById(8)).thenReturn(true);
                when(staffRepository.existsById(anyInt())).thenReturn(true);
                when(staffRepository.existsByStaffIdAndIsActiveTrueAndStaffPosition(anyInt(), anyString()))
                                .thenReturn(true);
                when(ticketAgencyRepository.existsByStopPointIdAndIsActiveTrue(1)).thenReturn(true);
                when(ticketAgencyRepository.existsByStopPointIdAndIsActiveTrue(8)).thenReturn(true);
                when(cargoTicketRepository.existsByTicketCodeIgnoreCase(anyString())).thenReturn(false);
                when(cargoTicketRepository.save(any(CargoTicket.class))).thenAnswer(invocation -> {
                        CargoTicket saved = invocation.getArgument(0);
                        saved.setCargoTicketId(55);
                        return saved;
                });
                when(transactionIdGenerator.generateUniqueTransactionId()).thenReturn("TX-1");
                when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
                when(coachStopRepository.findById(anyInt())).thenReturn(Optional.empty());
                when(paymentRepository.findByCargoTicket_CargoTicketId(55)).thenReturn(Optional.empty());
                when(cargoTicketRepository.findResponsibilityByCargoTicketId(55)).thenReturn(Optional.empty());

                CargoTicketResponse response = cargoTicketService.createCargoTicket(request, 123);

                assertNull(response.getTripId());
                assertEquals("RECEIVED", response.getStatus());
                assertEquals(55, response.getCargoTicketId());

                ArgumentCaptor<CargoTicket> ticketCaptor = ArgumentCaptor.forClass(CargoTicket.class);
                verify(cargoTicketRepository).save(ticketCaptor.capture());
                assertNull(ticketCaptor.getValue().getTripId());
                verify(tripRepository, never()).findById(anyInt());
                verify(cargoTicketDetailRepository, never()).sumActiveVolumeByTripId(anyInt());
        }

        @Test
        @DisplayName("Update rejects assigning an IN_PROGRESS trip")
        void rejectsAssigningStartedTripOnUpdate() {
                request.setTripId(77);
                request.setPaymentMethod("CASH");

                CargoTicket ticket = CargoTicket.builder()
                                .cargoTicketId(55)
                                .ticketCode("CG-TEST")
                                .tripId(null)
                                .pickupStopId(1)
                                .dropoffStopId(8)
                                .senderName("TestSender")
                                .senderPhone("0900000001")
                                .receiverName("TestReceiver")
                                .receiverPhone("0900000002")
                                .totalPrice(BigDecimal.valueOf(100000))
                                .feePayer("SENDER")
                                .codAmount(BigDecimal.ZERO)
                                .status("RECEIVED")
                                .soldBy(currentStaff)
                                .build();
                Trip startedTrip = Trip.builder()
                                .tripId(77)
                                .status("IN_PROGRESS")
                                .build();

                when(cargoTicketRepository.findByCargoTicketIdAndStatusNot(55, "ABANDONED"))
                                .thenReturn(Optional.of(ticket));
                when(paymentRepository.findByCargoTicket_CargoTicketId(55)).thenReturn(Optional.empty());
                when(tripRepository.findById(77)).thenReturn(Optional.of(startedTrip));

                var exception = assertThrows(
                                BusinessRuleException.class,
                                () -> cargoTicketService.updateCargoTicket(55, request));

                assertEquals(
                                "Chuyến xe đã khởi hành hoặc không còn hoạt động, không thể nhận thêm hàng.",
                                exception.getMessage());
                verify(cargoTicketRepository, never()).save(any(CargoTicket.class));
        }

        @Test
        @DisplayName("Capacity check is skipped when tripId is null")
        void skipsCapacityWhenTripIsNull() {
                assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(
                                cargoTicketService,
                                "validateCapacity",
                                null,
                                BigDecimal.valueOf(9),
                                BigDecimal.ZERO));
                verify(cargoTicketDetailRepository, never()).sumActiveVolumeByTripId(anyInt());
        }

        @Test
        @DisplayName("Capacity check enforces limit when tripId is set")
        void enforcesCapacityWhenTripIsSet() {
                when(cargoTicketDetailRepository.sumActiveVolumeByTripId(77))
                                .thenReturn(BigDecimal.valueOf(2.4));

                var exception = assertThrows(BusinessRuleException.class, () -> ReflectionTestUtils.invokeMethod(
                                cargoTicketService,
                                "validateCapacity",
                                77,
                                BigDecimal.valueOf(0.2),
                                BigDecimal.ZERO));

                assertEquals("Khoang hàng của chuyến xe vượt quá sức chứa 2,5 m³.", exception.getMessage());
        }

        @Test
        @DisplayName("Assignable board keeps only eligible unassigned RECEIVED orders")
        void assignableBoardFiltersByEligibility() {
                Trip scheduled = Trip.builder().tripId(77).status("SCHEDULED").build();
                CargoTicket eligible = unassignedTicket(11, "CG-11");
                CargoTicket ineligible = unassignedTicket(12, "CG-12");
                CargoTicket unpaidSender = unassignedTicket(13, "CG-13");
                Payment paid = completedPayment(11);
                Payment unpaid = pendingBankPayment(13);

                when(staffRepository.findByAccountId(123)).thenReturn(Optional.of(currentStaff));
                when(tripRepository.findById(77)).thenReturn(Optional.of(scheduled));
                when(cargoTicketDetailRepository.sumActiveVolumeByTripId(77)).thenReturn(BigDecimal.valueOf(0.5));
                when(cargoTicketRepository.findUnassignedReceivedByAgency(2))
                                .thenReturn(List.of(eligible, ineligible, unpaidSender));
                when(tripRepository.isEligibleForAgencyCargo(eq(77), eq(1), eq(8), eq(2)))
                                .thenReturn(true, false, true);
                when(cargoTicketDetailRepository.sumVolumeByCargoTicketId(11)).thenReturn(BigDecimal.valueOf(0.3));
                when(cargoTicketDetailRepository.sumVolumeByCargoTicketId(13)).thenReturn(BigDecimal.valueOf(0.2));
                when(paymentRepository.findByCargoTicket_CargoTicketId(11)).thenReturn(Optional.of(paid));
                when(paymentRepository.findByCargoTicket_CargoTicketId(13)).thenReturn(Optional.of(unpaid));
                when(coachStopRepository.findById(anyInt())).thenReturn(Optional.empty());

                CargoAssignableBoardResponse board = cargoTicketService.getAssignableCargo(77, 123);

                assertEquals(77, board.getTripId());
                assertEquals(0, board.getUsedCargoVolume().compareTo(BigDecimal.valueOf(0.5)));
                assertEquals(1, board.getTickets().size());
                assertEquals(11, board.getTickets().get(0).getCargoTicketId());
                assertEquals(0, board.getTickets().get(0).getOccupiedVolume().compareTo(BigDecimal.valueOf(0.3)));
        }

        @Test
        @DisplayName("Batch assign attaches selected unassigned orders under capacity")
        void batchAssignSucceedsUnderCapacity() {
                Trip scheduled = Trip.builder().tripId(77).status("SCHEDULED").build();
                CargoTicket first = unassignedTicket(11, "CG-11");
                CargoTicket second = unassignedTicket(12, "CG-12");

                when(staffRepository.findByAccountId(123)).thenReturn(Optional.of(currentStaff));
                when(tripRepository.findById(77)).thenReturn(Optional.of(scheduled));
                when(cargoTicketRepository.findByCargoTicketIdAndStatusNot(11, "ABANDONED"))
                                .thenReturn(Optional.of(first));
                when(cargoTicketRepository.findByCargoTicketIdAndStatusNot(12, "ABANDONED"))
                                .thenReturn(Optional.of(second));
                when(tripRepository.isEligibleForAgencyCargo(eq(77), eq(1), eq(8), eq(2))).thenReturn(true);
                when(cargoTicketDetailRepository.sumVolumeByCargoTicketId(11)).thenReturn(BigDecimal.valueOf(0.4));
                when(cargoTicketDetailRepository.sumVolumeByCargoTicketId(12)).thenReturn(BigDecimal.valueOf(0.5));
                when(cargoTicketDetailRepository.sumActiveVolumeByTripId(77))
                                .thenReturn(BigDecimal.valueOf(1.0), BigDecimal.valueOf(1.9));
                when(paymentRepository.findByCargoTicket_CargoTicketId(11))
                                .thenReturn(Optional.of(completedPayment(11)));
                when(paymentRepository.findByCargoTicket_CargoTicketId(12))
                                .thenReturn(Optional.of(completedPayment(12)));
                when(cargoTicketRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
                when(coachStopRepository.findById(anyInt())).thenReturn(Optional.empty());
                when(cargoTicketRepository.findResponsibilityByCargoTicketId(anyInt())).thenReturn(Optional.empty());

                CargoTripAssignResponse response = cargoTicketService.assignCargoToTrip(
                                77,
                                CargoTripAssignRequest.builder().cargoTicketIds(List.of(11, 12)).build(),
                                123);

                assertEquals(2, response.getAssignedCount());
                assertEquals(77, first.getTripId());
                assertEquals(77, second.getTripId());
                assertEquals(0, response.getUsedCargoVolume().compareTo(BigDecimal.valueOf(1.9)));
                verify(cargoTicketRepository).saveAll(anyList());
        }

        @Test
        @DisplayName("Batch assign rejects when selected volume exceeds remaining capacity")
        void batchAssignRejectsOverCapacity() {
                Trip scheduled = Trip.builder().tripId(77).status("SCHEDULED").build();
                CargoTicket ticket = unassignedTicket(11, "CG-11");

                when(staffRepository.findByAccountId(123)).thenReturn(Optional.of(currentStaff));
                when(tripRepository.findById(77)).thenReturn(Optional.of(scheduled));
                when(cargoTicketRepository.findByCargoTicketIdAndStatusNot(11, "ABANDONED"))
                                .thenReturn(Optional.of(ticket));
                when(tripRepository.isEligibleForAgencyCargo(eq(77), eq(1), eq(8), eq(2))).thenReturn(true);
                when(paymentRepository.findByCargoTicket_CargoTicketId(11))
                                .thenReturn(Optional.of(completedPayment(11)));
                when(cargoTicketDetailRepository.sumVolumeByCargoTicketId(11)).thenReturn(BigDecimal.valueOf(1.0));
                when(cargoTicketDetailRepository.sumActiveVolumeByTripId(77)).thenReturn(BigDecimal.valueOf(2.0));

                var exception = assertThrows(BusinessRuleException.class, () -> cargoTicketService.assignCargoToTrip(
                                77,
                                CargoTripAssignRequest.builder().cargoTicketIds(List.of(11)).build(),
                                123));

                assertEquals("Khoang hàng của chuyến xe vượt quá sức chứa 2,5 m³.", exception.getMessage());
                verify(cargoTicketRepository, never()).saveAll(anyList());
                assertNull(ticket.getTripId());
        }

        @Test
        @DisplayName("Batch assign rejects IN_PROGRESS trip")
        void batchAssignRejectsStartedTrip() {
                Trip started = Trip.builder().tripId(77).status("IN_PROGRESS").build();
                when(staffRepository.findByAccountId(123)).thenReturn(Optional.of(currentStaff));
                when(tripRepository.findById(77)).thenReturn(Optional.of(started));

                var exception = assertThrows(BusinessRuleException.class, () -> cargoTicketService.assignCargoToTrip(
                                77,
                                CargoTripAssignRequest.builder().cargoTicketIds(List.of(11)).build(),
                                123));

                assertEquals(
                                "Chuyến xe đã khởi hành hoặc không còn hoạt động, không thể nhận thêm hàng.",
                                exception.getMessage());
        }

        @Test
        @DisplayName("Batch assign rejects order that already has a trip")
        void batchAssignRejectsAlreadyAssignedOrder() {
                Trip scheduled = Trip.builder().tripId(77).status("SCHEDULED").build();
                CargoTicket ticket = unassignedTicket(11, "CG-11");
                ticket.setTripId(55);

                when(staffRepository.findByAccountId(123)).thenReturn(Optional.of(currentStaff));
                when(tripRepository.findById(77)).thenReturn(Optional.of(scheduled));
                when(cargoTicketRepository.findByCargoTicketIdAndStatusNot(11, "ABANDONED"))
                                .thenReturn(Optional.of(ticket));

                var exception = assertThrows(BusinessRuleException.class, () -> cargoTicketService.assignCargoToTrip(
                                77,
                                CargoTripAssignRequest.builder().cargoTicketIds(List.of(11)).build(),
                                123));

                assertEquals("Đơn CG-11 đã được gán cho một chuyến xe.", exception.getMessage());
                verify(cargoTicketRepository, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("Batch assign rejects SENDER bank transfer still PENDING")
        void batchAssignRejectsPendingSenderBankPayment() {
                Trip scheduled = Trip.builder().tripId(77).status("SCHEDULED").build();
                CargoTicket ticket = unassignedTicket(11, "CG-11");

                when(staffRepository.findByAccountId(123)).thenReturn(Optional.of(currentStaff));
                when(tripRepository.findById(77)).thenReturn(Optional.of(scheduled));
                when(cargoTicketRepository.findByCargoTicketIdAndStatusNot(11, "ABANDONED"))
                                .thenReturn(Optional.of(ticket));
                when(tripRepository.isEligibleForAgencyCargo(eq(77), eq(1), eq(8), eq(2))).thenReturn(true);
                when(paymentRepository.findByCargoTicket_CargoTicketId(11))
                                .thenReturn(Optional.of(pendingBankPayment(11)));

                var exception = assertThrows(BusinessRuleException.class, () -> cargoTicketService.assignCargoToTrip(
                                77,
                                CargoTripAssignRequest.builder().cargoTicketIds(List.of(11)).build(),
                                123));

                assertEquals(
                                "Người gửi chưa thanh toán xong (đơn CG-11), không thể gán chuyến. Hoàn tất chuyển khoản hoặc thu tiền trước.",
                                exception.getMessage());
                verify(cargoTicketRepository, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("Batch assign allows RECEIVER unpaid pending bank transfer")
        void batchAssignAllowsPendingReceiverBankPayment() {
                Trip scheduled = Trip.builder().tripId(77).status("SCHEDULED").build();
                CargoTicket ticket = unassignedTicket(11, "CG-11");
                ticket.setFeePayer("RECEIVER");

                when(staffRepository.findByAccountId(123)).thenReturn(Optional.of(currentStaff));
                when(tripRepository.findById(77)).thenReturn(Optional.of(scheduled));
                when(cargoTicketRepository.findByCargoTicketIdAndStatusNot(11, "ABANDONED"))
                                .thenReturn(Optional.of(ticket));
                when(tripRepository.isEligibleForAgencyCargo(eq(77), eq(1), eq(8), eq(2))).thenReturn(true);
                when(cargoTicketDetailRepository.sumVolumeByCargoTicketId(11)).thenReturn(BigDecimal.valueOf(0.4));
                when(cargoTicketDetailRepository.sumActiveVolumeByTripId(77))
                                .thenReturn(BigDecimal.ZERO, BigDecimal.valueOf(0.4));
                when(paymentRepository.findByCargoTicket_CargoTicketId(11))
                                .thenReturn(Optional.of(pendingBankPayment(11)));
                when(cargoTicketRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
                when(coachStopRepository.findById(anyInt())).thenReturn(Optional.empty());
                when(cargoTicketRepository.findResponsibilityByCargoTicketId(anyInt())).thenReturn(Optional.empty());

                CargoTripAssignResponse response = cargoTicketService.assignCargoToTrip(
                                77,
                                CargoTripAssignRequest.builder().cargoTicketIds(List.of(11)).build(),
                                123);

                assertEquals(1, response.getAssignedCount());
                assertEquals(77, ticket.getTripId());
        }

        @Test
        @DisplayName("Create with trip rejects SENDER bank transfer before payment")
        void createWithTripRejectsSenderBank() {
                request.setTripId(77);
                request.setFeePayer("SENDER");
                request.setPaymentMethod("BANK_TRANSFER");

                when(staffRepository.findByAccountId(123)).thenReturn(Optional.of(currentStaff));

                var exception = assertThrows(BusinessRuleException.class,
                                () -> cargoTicketService.createCargoTicket(request, 123));

                assertEquals(
                                "Người gửi thanh toán chuyển khoản: tạo đơn chưa gán chuyến, thanh toán xong rồi gán chuyến sau.",
                                exception.getMessage());
                verify(cargoTicketRepository, never()).save(any(CargoTicket.class));
        }

        private CargoTicket unassignedTicket(int id, String code) {
                return CargoTicket.builder()
                                .cargoTicketId(id)
                                .ticketCode(code)
                                .tripId(null)
                                .pickupStopId(1)
                                .dropoffStopId(8)
                                .senderName("Sender")
                                .senderPhone("0900000001")
                                .receiverName("Receiver")
                                .receiverPhone("0900000002")
                                .totalPrice(BigDecimal.valueOf(100000))
                                .feePayer("SENDER")
                                .codAmount(BigDecimal.ZERO)
                                .status("RECEIVED")
                                .soldBy(currentStaff)
                                .build();
        }

        private Payment completedPayment(int cargoTicketId) {
                return Payment.builder()
                                .paymentId(cargoTicketId)
                                .paymentMethod("CASH")
                                .status("COMPLETED")
                                .amount(BigDecimal.TEN)
                                .refundAmount(BigDecimal.ZERO)
                                .build();
        }

        private Payment pendingBankPayment(int cargoTicketId) {
                return Payment.builder()
                                .paymentId(cargoTicketId)
                                .paymentMethod("BANK_TRANSFER")
                                .status("PENDING")
                                .amount(BigDecimal.TEN)
                                .refundAmount(BigDecimal.ZERO)
                                .build();
        }
}
