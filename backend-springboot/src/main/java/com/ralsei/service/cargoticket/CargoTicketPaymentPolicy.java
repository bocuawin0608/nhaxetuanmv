package com.ralsei.service.cargoticket;

import java.math.BigDecimal;

import com.ralsei.model.CargoTicket;
import com.ralsei.model.Payment;

/**
 * Payment rules for cargo tickets: when money must be collected, how cash is
 * completed, and how cancel creates a refund request without manager payout.
 */
public interface CargoTicketPaymentPolicy {

    String FEE_SENDER = "SENDER";
    String FEE_RECEIVER = "RECEIVER";
    String METHOD_CASH = "CASH";
    String METHOD_BANK = "BANK_TRANSFER";
    String STATUS_PENDING = "PENDING";
    String STATUS_COMPLETED = "COMPLETED";
    String STATUS_FAILED = "FAILED";

    Payment requirePayment(CargoTicket ticket);

    Payment findPayment(CargoTicket ticket);

    boolean isCompleted(Payment payment);

    boolean isSender(CargoTicket ticket);

    boolean isReceiver(CargoTicket ticket);

    void requireCompleted(Payment payment, String message);

    /** SENDER cash is collected at counter when the order is created. */
    void completeCashIfApplicableOnCreate(Payment payment, String feePayer);

    void markCashCompleted(Payment payment);

    /** Blocks trip load when origin customer (SENDER) has not finished paying. */
    void requireSenderPaidBeforeLoad(CargoTicket ticket);

    /**
     * Trip assignment reserves coach capacity. SENDER must already have paid
     * (cash at counter or completed bank transfer). RECEIVER pays at destination,
     * so pending receiver payment does not block assignment.
     */
    void requireReadyForTripAssignment(CargoTicket ticket);

    /** Soft check used when building the assignable board. */
    boolean isReadyForTripAssignment(CargoTicket ticket);

    /**
     * Create-with-trip cannot complete bank payment in the same request, so
     * SENDER + BANK_TRANSFER must defer trip assignment until after QR success.
     */
    void rejectSenderBankCreateWithTrip(Integer tripId, String feePayer, String paymentMethod);

    /**
     * Destination hand-off: RECEIVER must pay before DELIVERED.
     * Cash can be completed in the same confirm call; bank must already be paid.
     */
    void settleReceiverPaymentBeforeDeliver(CargoTicket ticket);

    void requireSenderPaymentMethodOnCreate(String feePayer, String paymentMethod);

    void rejectMoneyChangesWhenPaid(Payment payment);

    void syncAmountIfPending(Payment payment, BigDecimal total);

    void applyCancelPaymentSideEffects(CargoTicket ticket);

    void createRefundRequest(Payment payment, String reason);
}
