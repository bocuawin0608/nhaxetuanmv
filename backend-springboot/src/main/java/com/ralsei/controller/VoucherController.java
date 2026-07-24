package com.ralsei.controller;

import com.ralsei.dto.request.voucher.CreateVoucherRequest;
import com.ralsei.dto.request.voucher.UpdateVoucherRequest;
import com.ralsei.dto.request.voucher.VoucherFilterRequest;
import com.ralsei.dto.response.PagedResponse;
import com.ralsei.dto.response.voucher.VoucherMetricsResponse;
import com.ralsei.dto.response.voucher.VoucherResponse;
import com.ralsei.service.VoucherService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Vouchers", description = "Voucher management")
@RestController
@RequestMapping("/api/v1/vouchers")
@RequiredArgsConstructor
/**
 * Handles HTTP requests for voucher operations.
 */
public class VoucherController {
    private final VoucherService voucherService;

    @PostMapping
    /**
     * Creates the voucher.
     *
     * @param request the value supplied for this operation
     *
     * @return the created voucher
     */
    public ResponseEntity<VoucherResponse> createVoucher(@Valid @RequestBody CreateVoucherRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(voucherService.createVoucher(request));
    }

    @GetMapping("/{id}")
    /**
     * Returns the voucher by id.
     *
     * @param id the value supplied for this operation
     *
     * @return the voucher by id
     */
    public ResponseEntity<VoucherResponse> getVoucherById(@PathVariable Integer id) {
        return ResponseEntity.ok(voucherService.getVoucherById(id));
    }

    @GetMapping
    /**
     * Returns the all vouchers.
     *
     * @param filterRequest the value supplied for this operation
     *
     * @return the all vouchers
     */
    public ResponseEntity<PagedResponse<VoucherResponse>> getAllVouchers(@Valid @ModelAttribute VoucherFilterRequest filterRequest) {
        return ResponseEntity.ok(voucherService.getAllVouchers(filterRequest));
    }

    @PutMapping("/{id}")
    /**
     * Updates the voucher.
     *
     * @param id the value supplied for this operation
     * @param request the value supplied for this operation
     *
     * @return the updated voucher
     */
    public ResponseEntity<VoucherResponse> updateVoucher(@PathVariable Integer id, @Valid @RequestBody UpdateVoucherRequest request) {
        return ResponseEntity.ok(voucherService.updateVoucher(id, request));
    }

    @DeleteMapping("/{id}")
    /**
     * Deletes the voucher.
     *
     * @param id the value supplied for this operation
     */
    public ResponseEntity<Void> deleteVoucher(@PathVariable Integer id) {
        voucherService.deleteVoucher(id);
        return ResponseEntity.noContent().build();
    }

    @Hidden
    @Deprecated
    @GetMapping("/metrics")
    public ResponseEntity<VoucherMetricsResponse> getVoucherMetrics() {
        return ResponseEntity.ok(voucherService.getVoucherMetrics());
    }
}
