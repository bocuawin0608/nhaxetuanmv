package com.ralsei.model.enums;

import com.ralsei.exception.BusinessRuleException;

/**
 * Outbound refund channel persisted in {@code refund.refundMethod} and
 * enforced by the database {@code CK_Refund_Method} constraint.
 */
/**
 * Provides the refund method component for the application.
 */
public enum RefundMethod {
    /** Manual bank transfer to the customer account stored in callback data. */
    BANK_TRANSFER,
    /** Cash payout at the ticket counter. */
    CASH;

    /**
     * Parses a trimmed refund method from API input or persisted rows.
     *
     * @param value raw method text, may be {@code null}
     * @return matching enum constant, or {@code null} when input is blank
     * @throws BusinessRuleException when the value is not a supported method
     */
    /**
     * Executes the from value operation.
     *
     * @param value the value supplied for this operation
     *
     * @return the operation result
     */
    public static RefundMethod fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase();
        try {
            return RefundMethod.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new BusinessRuleException("Phương thức hoàn tiền không hợp lệ.");
        }
    }
}
