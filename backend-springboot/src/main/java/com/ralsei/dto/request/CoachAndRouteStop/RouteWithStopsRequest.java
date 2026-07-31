package com.ralsei.dto.request.CoachAndRouteStop;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
/**
 * Represents the request payload for route with stops operations.
 */
public class RouteWithStopsRequest {
    @NotBlank(message = "Route name is required")
    @Size(max = 255, message = "Route name must be less than 255 characters")
    @Pattern(regexp = "^(Hà Nội - Quảng Trị|Quảng Trị - Hà Nội)$", message = "Tên tuyến đường không hợp lệ. Chỉ chấp nhận: Hà Nội - Quảng Trị hoặc Quảng Trị - Hà Nội")
    private String routeName;

    @NotNull(message = "Route stops are required")
    @Size(min = 2, message = "A route must have at least 2 stops")
    @Valid
    private List<RouteStopCreateRequest> routeStops;
}
