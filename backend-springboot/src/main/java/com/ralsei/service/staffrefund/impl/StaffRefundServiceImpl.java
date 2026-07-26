package com.ralsei.service.staffrefund.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.ralsei.dto.notification.PassengerRefundCompletedEmailPayload;
import com.ralsei.dto.projection.staffrefund.StaffPassengerRefundRowProjection;
import com.ralsei.dto.request.staffrefund.StaffRefundCompleteRequest;
import com.ralsei.dto.response.PagedResponse;
import com.ralsei.dto.response.staffrefund.StaffRefundDetailResponse;
import com.ralsei.dto.response.staffrefund.StaffRefundListItemResponse;
import com.ralsei.exception.BusinessRuleException;
import com.ralsei.exception.ResourceNotFoundException;
import com.ralsei.model.Refund;
import com.ralsei.model.Staff;
import com.ralsei.model.enums.RefundMethod;
import com.ralsei.model.enums.RefundStatus;
import com.ralsei.repository.RefundRepository;
import com.ralsei.repository.StaffRepository;
import com.ralsei.service.notification.PassengerTicketEmailAssembler;
import com.ralsei.service.notification.TicketEmailService;
import com.ralsei.service.staffrefund.StaffRefundService;
import com.ralsei.util.PhoneNumberUtility;
import com.ralsei.util.RefundCallbackDataParser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implements manager refund search, detail review, and payout confirmation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
/**
 * Provides the staff refund service impl component for the application.
 */
public class StaffRefundServiceImpl implements StaffRefundService {

    private static final Pattern BANK_TRANSFER_TRANSACTION_PATTERN =
        Pattern.compile("^[A-Za-z0-9-]{4,100}$");
    private static final Pattern TICKET_CODE_PATTERN =
        Pattern.compile("[A-Za-z0-9_-]{3,64}");
    private static final Pattern PHONE_SEARCH_PATTERN =
        Pattern.compile("^[0-9]{3,11}$");
    private static final String UNKNOWN_STAFF_DISPLAY = "Không xác định";

    private final RefundRepository refundRepository;
    private final StaffRepository staffRepository;
    private final RefundCallbackDataParser callbackDataParser;
    private final PassengerTicketEmailAssembler passengerTicketEmailAssembler;
    private final TicketEmailService ticketEmailService;

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<StaffRefundListItemResponse> searchPassengerRefunds(
        String status,
        String ticketCode,
        String phone,
        LocalDate createdFrom,
        LocalDate createdTo,
        int page,
        int size
    ) {
        SearchParams params = normalizeSearchParams(status, ticketCode, phone, createdFrom, createdTo);

        long totalElements = refundRepository.countPassengerRefunds(
            params.status(),
            params.ticketCode(),
            params.phone(),
            params.createdFrom(),
            params.createdTo()
        );

        if (totalElements == 0) {
            return emptyPage(page, size);
        }

        int totalPages = (int) Math.ceil((double) totalElements / size);
        List<Integer> refundIds = refundRepository.findPassengerRefundIds(
            params.status(),
            params.ticketCode(),
            params.phone(),
            params.createdFrom(),
            params.createdTo(),
            page * size,
            size
        );

        if (refundIds.isEmpty()) {
            return emptyPage(page, size);
        }

        List<StaffRefundListItemResponse> content = refundRepository
            .findPassengerRefundRowsByRefundIds(refundIds)
            .stream()
            .map(this::mapListItem)
            .toList();

        return new PagedResponse<>(
            content,
            page,
            size,
            totalElements,
            totalPages,
            page >= totalPages - 1
        );
    }

    @Override
    @Transactional(readOnly = true)
    /**
     * Returns the passenger refund detail.
     *
     * @param refundId the value supplied for this operation
     *
     * @return the passenger refund detail
     */
    public StaffRefundDetailResponse getPassengerRefundDetail(int refundId) {
        StaffPassengerRefundRowProjection row = refundRepository.findPassengerRefundRowByRefundId(refundId);
        if (row == null) {
            throw new ResourceNotFoundException("Không tìm thấy yêu cầu hoàn tiền.");
        }
        return mapDetail(row);
    }

    @Override
    @Transactional
    public StaffRefundDetailResponse completePassengerRefund(
        Integer accountId,
        int refundId,
        StaffRefundCompleteRequest request
    ) {
        validateAccountId(accountId);

        Refund refund = refundRepository.findById(refundId)
            .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu hoàn tiền."));

        if (!RefundStatus.PENDING.name().equals(refund.getStatus())) {
            throw new BusinessRuleException("Chỉ có thể xác nhận yêu cầu hoàn tiền đang chờ xử lý.");
        }

        StaffPassengerRefundRowProjection row = refundRepository.findPassengerRefundRowByRefundId(refundId);
        if (row == null || row.getPassengerTicketId() == null) {
            throw new ResourceNotFoundException("Không tìm thấy yêu cầu hoàn tiền hành khách.");
        }

        RefundMethod refundMethod = RefundMethod.fromValue(refund.getRefundMethod());
        String transactionId = resolveTransactionId(refundMethod, refundId, request.transactionId());

        LocalDateTime refundTime = LocalDateTime.now();
        refund.setStatus(RefundStatus.COMPLETED.name());
        refund.setRefundTime(refundTime);
        refund.setTransactionId(transactionId);
        refund.setUpdatedBy(accountId);
        refundRepository.save(refund);

        PassengerRefundCompletedEmailPayload emailPayload = new PassengerRefundCompletedEmailPayload(
            passengerTicketEmailAssembler.assemble(row.getPassengerTicketId()),
            refund.getAmount(),
            refundTime,
            transactionId,
            refund.getRefundMethod()
        );
        sendRefundCompletedEmailAfterCommit(emailPayload, refundId);

        StaffPassengerRefundRowProjection updatedRow =
            refundRepository.findPassengerRefundRowByRefundId(refundId);
        return mapDetail(updatedRow);
    }

    /**
     * Defers refund-completion email delivery until the payout confirmation
     * transaction commits. Failed SMTP delivery is logged without reverting the
     * already-valid refund completion record.
     */
    private void sendRefundCompletedEmailAfterCommit(
        PassengerRefundCompletedEmailPayload payload,
        int refundId
    ) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            /**
             * Executes the after commit operation.
             */
            public void afterCommit() {
                try {
                    ticketEmailService.sendRefundCompleted(payload);
                } catch (Exception exception) {
                    log.error("Failed to send refund completion email for refundId={}", refundId, exception);
                }
            }
        });
    }

    private SearchParams normalizeSearchParams(
        String status,
        String ticketCode,
        String phone,
        LocalDate createdFrom,
        LocalDate createdTo
    ) {
        RefundStatus parsedStatus = parseRefundSearchStatus(trimToNull(status));

        String normalizedTicketCode = trimToNull(ticketCode);
        if (normalizedTicketCode != null && !TICKET_CODE_PATTERN.matcher(normalizedTicketCode).matches()) {
            throw new IllegalArgumentException("Mã vé không hợp lệ.");
        }

        String normalizedPhone = trimToNull(phone);
        if (normalizedPhone != null) {
            normalizedPhone = PhoneNumberUtility.normalizeToLocalFormat(normalizedPhone);
            if (!PHONE_SEARCH_PATTERN.matcher(normalizedPhone).matches()) {
                throw new IllegalArgumentException("Số điện thoại không hợp lệ.");
            }
        }

        if (createdFrom != null && createdTo != null && createdFrom.isAfter(createdTo)) {
            throw new IllegalArgumentException("Ngày bắt đầu không được lớn hơn ngày kết thúc.");
        }

        return new SearchParams(
            parsedStatus != null ? parsedStatus.name() : null,
            normalizedTicketCode,
            normalizedPhone,
            createdFrom,
            createdTo
        );
    }

    private RefundStatus parseRefundSearchStatus(String value) {
        if (value == null) {
            return null;
        }
        try {
            return RefundStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Trạng thái hoàn tiền không hợp lệ.");
        }
    }

    private String resolveTransactionId(RefundMethod refundMethod, int refundId, String rawTransactionId) {
        String trimmed = trimToNull(rawTransactionId);

        if (refundMethod == RefundMethod.BANK_TRANSFER) {
            if (trimmed == null) {
                throw new BusinessRuleException("Vui lòng nhập mã giao dịch chuyển khoản.");
            }
            if (!BANK_TRANSFER_TRANSACTION_PATTERN.matcher(trimmed).matches()) {
                throw new BusinessRuleException("Mã giao dịch chuyển khoản không hợp lệ.");
            }
            return trimmed;
        }

        if (trimmed != null && trimmed.length() > 100) {
            throw new BusinessRuleException("Ghi chú biên nhận không được vượt quá 100 ký tự.");
        }
        if (trimmed != null) {
            return trimmed;
        }
        return "CASH-" + refundId + "-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
    }

    private StaffRefundListItemResponse mapListItem(StaffPassengerRefundRowProjection row) {
        return new StaffRefundListItemResponse(
            row.getRefundId(),
            row.getTicketCode(),
            row.getCustomerName(),
            row.getCustomerPhone(),
            row.getAmount(),
            row.getStatus(),
            row.getRefundMethod(),
            row.getReason()
        );
    }

    private StaffRefundDetailResponse mapDetail(StaffPassengerRefundRowProjection row) {
        return new StaffRefundDetailResponse(
            row.getRefundId(),
            row.getPaymentId(),
            row.getTicketCode(),
            row.getCustomerName(),
            row.getCustomerPhone(),
            row.getAmount(),
            row.getStatus(),
            row.getRefundMethod(),
            row.getReason(),
            callbackDataParser.parse(row.getCallbackData()),
            row.getTransactionId(),
            row.getRefundTime(),
            row.getCreatedAt(),
            resolveCreatedByStaffDisplay(row.getCreatedBy()),
            row.getUpdatedAt(),
            resolveUpdatedByStaffDisplay(row.getUpdatedBy())
        );
    }

    private String resolveCreatedByStaffDisplay(Integer accountId) {
        if (accountId == null) {
            return UNKNOWN_STAFF_DISPLAY;
        }
        return staffRepository.findByAccountId(accountId)
            .map(this::formatStaffDisplay)
            .orElse(UNKNOWN_STAFF_DISPLAY);
    }

    private String resolveUpdatedByStaffDisplay(Integer accountId) {
        if (accountId == null) {
            return null;
        }
        return staffRepository.findByAccountId(accountId)
            .map(this::formatStaffDisplay)
            .orElse(UNKNOWN_STAFF_DISPLAY);
    }

    private String formatStaffDisplay(Staff staff) {
        String name = staff.getStaffName() != null ? staff.getStaffName().trim() : "";
        String phone = staff.getPhone() != null ? staff.getPhone().trim() : "";
        if (!name.isEmpty() && !phone.isEmpty()) {
            return name + " - " + phone;
        }
        if (!name.isEmpty()) {
            return name;
        }
        if (!phone.isEmpty()) {
            return phone;
        }
        return UNKNOWN_STAFF_DISPLAY;
    }

    private void validateAccountId(Integer accountId) {
        if (accountId == null || accountId < 1) {
            throw new BusinessRuleException("Không xác định được tài khoản quản lý.");
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private PagedResponse<StaffRefundListItemResponse> emptyPage(int page, int size) {
        return new PagedResponse<>(List.of(), page, size, 0, 0, true);
    }

    private record SearchParams(
        String status,
        String ticketCode,
        String phone,
        LocalDate createdFrom,
        LocalDate createdTo
    ) {}
}
