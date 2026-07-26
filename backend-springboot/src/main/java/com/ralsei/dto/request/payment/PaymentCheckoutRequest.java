package com.ralsei.dto.request.payment;

import java.math.BigDecimal;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * Represents the request payload for payment checkout operations.
 */
public class PaymentCheckoutRequest {
    private Integer passengerTicketId;
    private Integer cargoTicketId;

    @NotNull(message = "Payment amount is required")
    @DecimalMin(value = "0.01", message = "Payment amount must be greater than 0")
    private BigDecimal amount;

    @NotBlank(message = "Payment method is required")
    @Pattern(regexp = "CASH|BANK_TRANSFER", message = "Payment method must be CASH or BANK_TRANSFER")
    private String paymentMethod;

    @AssertTrue(message = "Payment must target exactly one ticket type")
    /**
     * Returns whether the single payment target is active.
     *
     * @return {@code true} if the single payment target is active; otherwise {@code false}
     */
    public boolean isSinglePaymentTarget() {
        return (passengerTicketId != null && cargoTicketId == null)
                || (passengerTicketId == null && cargoTicketId != null);
    }
}
