package com.ralsei.dto.request.CoachAndRouteStop;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Pattern;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
/**
 * Represents the request payload for route operations.
 */
public class RouteRequest {
    @NotBlank(message = "Route name is required")
    @Size(max = 255, message = "Route name must be less than 255 characters")
    @Pattern(regexp = "^(Hà Nội - Quảng Trị|Quảng Trị - Hà Nội)$", message = "Tên tuyến đường không hợp lệ. Chỉ chấp nhận: Hà Nội - Quảng Trị hoặc Quảng Trị - Hà Nội")
    private String routeName;

    @NotNull(message = "Total kilometers is required")
    @Positive(message = "Total kilometers must be greater than zero")
    @Max(value = 2147483647, message = "Total kilometers must be less than 2147483647")
    private BigDecimal totalKilometers;

    @Min(value = 1, message = "Total minutes must be at least 1")
    @Max(value = 2147483647, message = "Total minutes must be less than 2147483647")
    private int totalMinutes;
}
