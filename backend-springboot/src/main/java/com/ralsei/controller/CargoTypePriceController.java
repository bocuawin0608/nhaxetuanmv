package com.ralsei.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ralsei.dto.request.cargotypeprice.CargoTypePriceRequest;
import com.ralsei.dto.response.cargotypeprice.CargoTypePriceResponse;
import com.ralsei.dto.response.PagedResponse;
import com.ralsei.service.CargoTypePriceService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Cargo", description = "Cargo type pricing")
@RestController
@RequestMapping("/api/v1/manager/cargo-type-prices")
@RequiredArgsConstructor
@Validated
/**
 * Handles HTTP requests for cargo type price operations.
 */
public class CargoTypePriceController {

    private final CargoTypePriceService cargoTypePriceService;

    @GetMapping()
    public ResponseEntity<PagedResponse<CargoTypePriceResponse>> getCargoTypePrices(
            @RequestParam(required = false, defaultValue = "0") int cargoTypeId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(cargoTypePriceService.getAllCargoTypePrices(cargoTypeId, search, page, size));
    }

    @Hidden
    @Deprecated
    @GetMapping("/{id}")
    public ResponseEntity<CargoTypePriceResponse> getCargoTypePriceById(
            @PathVariable @Min(value = 1, message = "ID của bảng giá phải lớn hơn 0.") int id) {
        return ResponseEntity.ok(cargoTypePriceService.getCargoTypePriceById(id));
    }

    @PostMapping()
    public ResponseEntity<CargoTypePriceResponse> createCargoTypePrice(
            @Valid @RequestBody CargoTypePriceRequest request) {
        CargoTypePriceResponse response = cargoTypePriceService.createCargoTypePrice(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CargoTypePriceResponse> updateCargoTypePrice(
            @PathVariable @Min(value = 1, message = "ID của bảng giá phải lớn hơn 0.") int id,
            @Valid @RequestBody @NonNull CargoTypePriceRequest request) {
        CargoTypePriceResponse response = cargoTypePriceService.updateCargoTypePrice(id, request);
        return ResponseEntity.ok(response);
    }

    @Hidden
    @Deprecated
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCargoTypePrice(
            @PathVariable @Min(value = 1, message = "ID của bảng giá phải lớn hơn 0.") int id) {
        cargoTypePriceService.deleteCargoTypePrice(id);
        return ResponseEntity.noContent().build();
    }
}
