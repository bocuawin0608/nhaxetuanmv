package com.ralsei.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ralsei.dto.request.payment.PaymentCheckoutRequest;
import com.ralsei.dto.request.sePay.SepayWebhookRequest;
import com.ralsei.model.Payment;
import com.ralsei.service.PaymentService;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Payment", description = "Payment checkout and SePay IPN")
@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
/**
 * Handles HTTP requests for payment operations.
 */
public class PaymentController {

    private final PaymentService paymentService;

    @Value("${sepay.api.token}")
    private String sepayApiToken;

    @Hidden
    @Deprecated
    @PostMapping("/checkout")
    /**
     * Executes the initialize payment operation.
     *
     * @param request the value supplied for this operation
     *
     * @return the operation result
     */
    public ResponseEntity<?> initializePayment(@Valid @RequestBody PaymentCheckoutRequest request) {
        try {
            Payment payment = paymentService.initializePayment(request);
            return ResponseEntity.ok(Map.of(
                    "transactionId", payment.getTransactionId(),
                    "amount", payment.getAmount(),
                    "status", payment.getStatus()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/sepay-ipn")
    public ResponseEntity<?> handleSepayWebhook(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody SepayWebhookRequest request) {

        if (authHeader == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Missing Authorization header");
        }

        String token = authHeader.replace("Bearer ", "").replace("Apikey ", "").trim();
        if (!sepayApiToken.equals(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid API token");
        }

        try {
            paymentService.processWebhook(request);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @Hidden
    @Deprecated
    @GetMapping("/transaction/{transactionId}")
    /**
     * Returns the payment by transaction id.
     *
     * @param transactionId the value supplied for this operation
     *
     * @return the payment by transaction id
     */
    public ResponseEntity<?> getPaymentByTransactionId(@PathVariable String transactionId) {
        try {
            Payment payment = paymentService.getPaymentByTransactionId(transactionId);
            return ResponseEntity.ok(payment);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }
}
