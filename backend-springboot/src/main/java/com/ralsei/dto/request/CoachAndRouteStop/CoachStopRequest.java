package com.ralsei.dto.request.CoachAndRouteStop;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
/**
 * Represents the request payload for coach stop operations.
 */
public class CoachStopRequest {
    @NotBlank(message = "Stop point name is required")
    @Size(max = 255, message = "Stop point name must be less than 255 characters")
    private String stopPointName;

    @NotBlank(message = "Address is required")
    @Size(max = 255, message = "Address must be less than 255 characters")
    @Pattern(regexp = "^[\\p{L}\\p{N}\\s,.-]+$", message = "Địa chỉ không hợp lệ.")
    private String address;

    @NotBlank(message = "City is required")
    @Size(max = 255, message = "City must be less than 255 characters")
    @Pattern(regexp = "^(Hà Nội|Quảng Trị)$", message = "Thành phố không hợp lệ. Chỉ chấp nhận: Hà Nội hoặc Quảng Trị.")
    private String city;

}
