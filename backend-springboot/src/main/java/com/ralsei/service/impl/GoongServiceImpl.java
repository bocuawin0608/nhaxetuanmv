package com.ralsei.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.ralsei.dto.request.goong.CalculateRouteDistancesRequest;
import com.ralsei.dto.request.goong.DistanceTimeRequest;
import com.ralsei.dto.response.goong.CalculateRouteDistancesResponse;
import com.ralsei.dto.response.goong.DistanceTimeResponse;
import com.ralsei.dto.response.goong.GeocodeResponse;
import com.ralsei.model.RouteStop;
import com.ralsei.repository.RouteRepository;
import com.ralsei.repository.RouteStopRepository;
import com.ralsei.service.GoongService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
/**
 * Provides the goong service impl component for the application.
 */
public class GoongServiceImpl implements GoongService {

    @Value("${goong.api.key}")
    private String goongApiKey;

    private final RestTemplate restTemplate;
    private final RouteStopRepository routeStopRepository;
    private final RouteRepository routeRepository;

    @Override
    /**
     * Executes the autocomplete operation.
     *
     * @param input the value supplied for this operation
     *
     * @return the operation result
     */
    public Object autocomplete(String input) {
        String url = "https://rsapi.goong.io/v2/place/autocomplete?api_key=" + goongApiKey + "&input=" + input;
        return restTemplate.getForObject(url, Object.class);
    }

    @Override
    /**
     * Returns the distance and time.
     *
     * @param request the value supplied for this operation
     *
     * @return the distance and time
     */
    @Deprecated
    public DistanceTimeResponse getDistanceAndTime(DistanceTimeRequest request) {
        String origin = request.getOriginLat() + ", " + request.getOriginLng();
        String destination = request.getDestinationLat() + ", " + request.getDestinationLng();
        String url = "https://rsapi.goong.io/v2/direction?api_key=" + goongApiKey
                + "&origin=" + origin
                + "&destination=" + destination
                + "&vehicle=" + request.getVehicle();

        JsonNode response = restTemplate.getForObject(url, JsonNode.class);
        if (response == null || !response.has("routes") || response.path("routes").isEmpty()) {
            throw new RuntimeException("Lỗi khi gọi Goong API");
        }
        JsonNode leg = response.path("routes").path(0).path("legs").path(0);

        double distanceKm = leg.path("distance").path("value").asDouble() / 1000.0;
        double durationMinutes = leg.path("duration").path("value").asDouble() / 60.0;

        distanceKm = Math.round(distanceKm * 100.0) / 100.0;
        durationMinutes = Math.round(durationMinutes * 100.0) / 100.0;

        return new DistanceTimeResponse(distanceKm, durationMinutes);
    }

    @Override
    @Transactional
    /**
     * Executes the calculate route distances operation.
     *
     * @param request the value supplied for this operation
     *
     * @return the operation result
     */
    public CalculateRouteDistancesResponse calculateRouteDistances(CalculateRouteDistancesRequest request) {
        int routeId = request.getRouteId();
        List<RouteStop> stops = routeStopRepository.findByRoute_RouteIdOrderByStopOrderAsc(routeId);

        if (stops.size() < 2) {
            return CalculateRouteDistancesResponse.builder()
                    .message("Tuyến cần ít nhất 2 điểm dừng để tính khoảng cách.")
                    .updated(0)
                    .build();
        }

        calculateAndSetRouteStopsDistances(stops);
        routeStopRepository.saveAll(stops);

        // update route total kilometers and total minutes corresponding to the last
        // stops
        RouteStop lastStop = stops.get(stops.size() - 1);
        BigDecimal km = lastStop.getKilometersFromStart() != null ? lastStop.getKilometersFromStart() : BigDecimal.ZERO;
        routeRepository.updateRouteTotals(routeId, km, lastStop.getMinutesFromStart());

        return CalculateRouteDistancesResponse.builder()
                .message("Đã tính toán khoảng cách và thời gian cho " + (stops.size() - 1) + " điểm dừng.")
                .updated(stops.size() - 1)
                .build();
    }

    @Override
    /**
     * Executes the calculate and set route stops distances operation.
     *
     * @param sortedStops the value supplied for this operation
     */
    public void calculateAndSetRouteStopsDistances(List<RouteStop> sortedStops) {
        if (sortedStops == null || sortedStops.size() < 2) {
            return;
        }

        String origin = sortedStops.get(0).getCoachStop().getLatitude() + ","
                + sortedStops.get(0).getCoachStop().getLongitude();

        StringBuilder destinationBuilder = new StringBuilder();
        for (int i = 1; i < sortedStops.size(); i++) {
            if (i > 1) {
                destinationBuilder.append(";");
            }
            destinationBuilder.append(sortedStops.get(i).getCoachStop().getLatitude())
                    .append(",")
                    .append(sortedStops.get(i).getCoachStop().getLongitude());
        }
        String destination = destinationBuilder.toString();

        String url = "https://rsapi.goong.io/v2/direction?api_key=" + goongApiKey
                + "&origin=" + origin
                + "&destination=" + destination
                + "&vehicle=car";

        JsonNode response = restTemplate.getForObject(url, JsonNode.class);
        if (response == null || !response.has("routes") || response.path("routes").isEmpty()) {
            throw new RuntimeException("Lỗi khi tính toán khoảng cách từ Goong API");
        }
        JsonNode legs = response.path("routes").path(0).path("legs");

        sortedStops.get(0).setKilometersFromStart(BigDecimal.ZERO);
        sortedStops.get(0).setMinutesFromStart(0);

        double cumulativeKm = 0;
        double cumulativeMinutes = 0;

        for (int i = 0; i < legs.size(); i++) {
            JsonNode leg = legs.path(i);
            double distanceKm = leg.path("distance").path("value").asDouble() / 1000.0;
            double durationMinutes = leg.path("duration").path("value").asDouble() / 60.0;

            cumulativeKm += Math.round(distanceKm * 100.0) / 100.0;
            cumulativeMinutes += Math.round(durationMinutes * 100.0) / 100.0;

            BigDecimal km = BigDecimal.valueOf(cumulativeKm).setScale(2, RoundingMode.HALF_UP);
            int minutes = (int) Math.round(cumulativeMinutes);

            sortedStops.get(i + 1).setKilometersFromStart(km);
            sortedStops.get(i + 1).setMinutesFromStart(minutes);
        }
    }

    @Override
    /**
     * Executes the geocode operation.
     *
     * @param address the value supplied for this operation
     *
     * @return the operation result
     */
    public GeocodeResponse geocode(String address) {
        String url = "https://rsapi.goong.io/v2/geocode?api_key=" + goongApiKey + "&address=" + address;
        JsonNode response = restTemplate.getForObject(url, JsonNode.class);

        if (response == null || !response.has("results") || response.path("results").isEmpty()) {
            throw new RuntimeException("Không tìm thấy tọa độ cho địa chỉ: " + address);
        }

        JsonNode firstResult = response.path("results").path(0);
        JsonNode location = firstResult.path("geometry").path("location");

        return GeocodeResponse.builder()
                .latitude(BigDecimal.valueOf(location.path("lat").asDouble()))
                .longitude(BigDecimal.valueOf(location.path("lng").asDouble()))
                .formattedAddress(firstResult.path("formatted_address").asText())
                .build();
    }
}
