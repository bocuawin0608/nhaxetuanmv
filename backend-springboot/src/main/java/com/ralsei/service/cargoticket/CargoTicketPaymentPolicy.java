package com.ralsei.service.cargoticket;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Component;

import com.ralsei.exception.BusinessRuleException;
import com.ralsei.exception.ResourceNotFoundException;
import com.ralsei.model.CargoTicket;
import com.ralsei.model.Payment;
import com.ralsei.model.Refund;
import com.ralsei.repository.PaymentRepository;
import com.ralsei.repository.RefundRepository;

import lombok.RequiredArgsConstructor;

/**
 * Payment rules for cargo tickets: when money must be collected, how cash is
 * completed, and how cancel creates a refund request without manager payout.
 */
@Component
@RequiredArgsConstructor
public class CargoTicketPaymentPolicy {

    public static final String FEE_SENDER = "SENDER";
    public static final String FEE_RECEIVER = "RECEIVER";
    public static final String METHOD_CASH = "CASH";
    public static final String METHOD_BANK = "BANK_TRANSFER";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;

    public Payment requirePayment(CargoTicket ticket) {
        return paymentRepository.findByCargoTicket_CargoTicketId(ticket.getCargoTicketId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy thanh toán cho đơn gửi hàng ID: " + ticket.getCargoTicketId()));
    }

    public Payment findPayment(CargoTicket ticket) {
        return paymentRepository.findByCargoTicket_CargoTicketId(ticket.getCargoTicketId()).orElse(null);
    }

    public boolean isCompleted(Payment payment) {
        return payment != null && STATUS_COMPLETED.equals(payment.getStatus());
    }

    public boolean isSender(CargoTicket ticket) {
        return FEE_SENDER.equalsIgnoreCase(ticket.getFeePayer());
    }

    public boolean isReceiver(CargoTicket ticket) {
        return FEE_RECEIVER.equalsIgnoreCase(ticket.getFeePayer());
    }

    public void requireCompleted(Payment payment, String message) {
        if (!isCompleted(payment)) {
            throw new BusinessRuleException(message);
        }
    }

    /**
     * SENDER cash is collected at counter when the order is created.
     */
    public void completeCashIfApplicableOnCreate(Payment payment, String feePayer) {
        if (FEE_SENDER.equalsIgnoreCase(feePayer) && METHOD_CASH.equals(payment.getPaymentMethod())) {
            markCashCompleted(payment);
        }
    }

    public void markCashCompleted(Payment payment) {
        if (!METHOD_CASH.equals(payment.getPaymentMethod())) {
            throw new BusinessRuleException("Chỉ có thể hoàn thành thanh toán tiền mặt.");
        }
        if (!STATUS_PENDING.equals(payment.getStatus())) {
            throw new BusinessRuleException("Thanh toán đã được xử lý.");
        }
        payment.setStatus(STATUS_COMPLETED);
        payment.setPaymentTime(LocalDateTime.now());
        paymentRepository.save(payment);
    }

    /**
     * Blocks trip load when origin customer (SENDER) has not finished paying.
     */
    public void requireSenderPaidBeforeLoad(CargoTicket ticket) {
        if (!isSender(ticket)) {
            return;
        }
        Payment payment = requirePayment(ticket);
        requireCompleted(payment,
                "Người gửi chưa thanh toán xong, không thể xác nhận hàng lên xe.");
    }

    /**
     * Trip assignment reserves coach capacity. SENDER must already have paid
     * (cash at counter or completed bank transfer). RECEIVER pays at destination,
     * so pending receiver payment does not block assignment.
     */
    public void requireReadyForTripAssignment(CargoTicket ticket) {
        if (isReceiver(ticket)) {
            return;
        }
        Payment payment = findPayment(ticket);
        if (payment == null) {
            throw new BusinessRuleException(
                    "Đơn " + ticket.getTicketCode()
                            + " chưa có thanh toán, không thể gán chuyến.");
        }
        if (STATUS_FAILED.equals(payment.getStatus())) {
            throw new BusinessRuleException(
                    "Đơn " + ticket.getTicketCode()
                            + " thanh toán thất bại, không thể gán chuyến.");
        }
        requireCompleted(payment,
                "Người gửi chưa thanh toán xong (đơn " + ticket.getTicketCode()
                        + "), không thể gán chuyến. Hoàn tất chuyển khoản hoặc thu tiền trước.");
    }

    /** Soft check used when building the assignable board. */
    public boolean isReadyForTripAssignment(CargoTicket ticket) {
        if (isReceiver(ticket)) {
            return true;
        }
        Payment payment = findPayment(ticket);
        return isCompleted(payment);
    }

    /**
     * Create-with-trip cannot complete bank payment in the same request, so
     * SENDER + BANK_TRANSFER must defer trip assignment until after QR success.
     */
    public void rejectSenderBankCreateWithTrip(Integer tripId, String feePayer, String paymentMethod) {
        if (tripId == null) {
            return;
        }
        if (FEE_SENDER.equalsIgnoreCase(feePayer) && METHOD_BANK.equals(paymentMethod)) {
            throw new BusinessRuleException(
                    "Người gửi thanh toán chuyển khoản: tạo đơn chưa gán chuyến, "
                            + "thanh toán xong rồi gán chuyến sau.");
        }
    }

    /**
     * Destination hand-off: RECEIVER must pay before DELIVERED.
     * Cash can be completed in the same confirm call; bank must already be paid.
     */
    public void settleReceiverPaymentBeforeDeliver(CargoTicket ticket) {
        if (!isReceiver(ticket)) {
            Payment payment = findPayment(ticket);
            if (isSender(ticket)) {
                requireCompleted(payment,
                        "Người gửi chưa thanh toán xong, không thể xác nhận giao hàng.");
            }
            return;
        }
        Payment payment = findPayment(ticket);
        if (payment == null) {
            throw new BusinessRuleException(
                    "Chưa chọn hình thức thanh toán của người nhận (tiền mặt hoặc chuyển khoản).");
        }
        if (isCompleted(payment)) {
            return;
        }
        if (METHOD_CASH.equals(payment.getPaymentMethod())) {
            markCashCompleted(payment);
            return;
        }
        if (METHOD_BANK.equals(payment.getPaymentMethod())) {
            throw new BusinessRuleException(
                    "Người nhận chưa chuyển khoản xong. Vui lòng quét QR và chờ thanh toán thành công trước khi giao hàng.");
        }
        throw new BusinessRuleException("Người nhận chưa thanh toán, không thể xác nhận giao hàng.");
    }

    public void requireSenderPaymentMethodOnCreate(String feePayer, String paymentMethod) {
        if (FEE_SENDER.equalsIgnoreCase(feePayer)
                && (paymentMethod == null || paymentMethod.isBlank())) {
            throw new BusinessRuleException(
                    "Người gửi trả phí bắt buộc chọn phương thức thanh toán.");
        }
    }

    public void rejectMoneyChangesWhenPaid(Payment payment) {
        if (isCompleted(payment)) {
            throw new BusinessRuleException(
                    "Đơn đã thanh toán, không thể đổi người trả phí, phương thức hoặc số tiền cước. "
                            + "Hãy hủy đơn và tạo đơn mới nếu cần.");
        }
    }

    public void syncAmountIfPending(Payment payment, BigDecimal total) {
        if (payment == null) {
            return;
        }
        if (isCompleted(payment)) {
            if (payment.getAmount().compareTo(total) != 0) {
                throw new BusinessRuleException(
                        "Đơn đã thanh toán, không thể thay đổi số tiền.");
            }
            return;
        }
        payment.setAmount(total);
        paymentRepository.save(payment);
    }

    public void applyCancelPaymentSideEffects(CargoTicket ticket) {
        Payment payment = findPayment(ticket);
        if (payment == null) {
            return;
        }
        if (STATUS_PENDING.equals(payment.getStatus())) {
            payment.setStatus(STATUS_FAILED);
            paymentRepository.save(payment);
            return;
        }
        if (STATUS_COMPLETED.equals(payment.getStatus())) {
            createRefundRequest(payment, "Hủy đơn gửi hàng " + ticket.getTicketCode());
        }
    }

    public void createRefundRequest(Payment payment, String reason) {
        if (refundRepository.existsByPaymentIdAndStatusIn(
                payment.getPaymentId(), List.of(STATUS_PENDING, STATUS_COMPLETED))) {
            throw new BusinessRuleException("Đơn thanh toán này đã có yêu cầu hoàn tiền.");
        }
        payment.setRefundAmount(payment.getAmount());
        paymentRepository.save(payment);

        Refund refund = Refund.builder()
                .paymentId(payment.getPaymentId())
                .amount(payment.getAmount())
                .reason(reason)
                .refundMethod(payment.getPaymentMethod())
                .status(STATUS_PENDING)
                .build();
        refundRepository.save(refund);
    }
}
