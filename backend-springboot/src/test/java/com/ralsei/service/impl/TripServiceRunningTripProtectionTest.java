package com.ralsei.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ralsei.dto.request.trip.TripUpdateRequest;
import com.ralsei.model.Trip;
import com.ralsei.repository.RouteRepository;
import com.ralsei.repository.StaffRepository;
import com.ralsei.repository.TripRepository;

@ExtendWith(MockitoExtension.class)
class TripServiceRunningTripProtectionTest {

    @Mock private TripRepository tripRepository;
    @Mock private RouteRepository routeRepository;
    @Mock private StaffRepository staffRepository;

    @InjectMocks
    private TripServiceImpl tripService;

    @Test
    void updateRejectsInProgressTripBeforeValidatingOrWriting() {
        Trip runningTrip = Trip.builder().tripId(289).status("IN_PROGRESS").build();
        TripUpdateRequest request = new TripUpdateRequest(
                1, 10, 73, 6, LocalDateTime.now().plusDays(1), "SCHEDULED");
        when(tripRepository.findById(289)).thenReturn(Optional.of(runningTrip));

        String result = tripService.updateTrip(289, request);

        assertEquals("Chuyến xe đã bắt đầu hoặc kết thúc, chỉ có thể xem thông tin.", result);
        verify(tripRepository).findById(289);
        verifyNoMoreInteractions(tripRepository);
        verifyNoInteractions(routeRepository, staffRepository);
    }

    @Test
    void deleteRejectsInProgressTripWithoutCancellingIt() {
        Trip runningTrip = Trip.builder().tripId(289).status("IN_PROGRESS").build();
        when(tripRepository.findById(289)).thenReturn(Optional.of(runningTrip));

        String result = tripService.deleteTrip(289);

        assertEquals("Chuyến xe đã bắt đầu hoặc kết thúc, chỉ có thể xem thông tin.", result);
        verify(tripRepository).findById(289);
        verifyNoMoreInteractions(tripRepository);
    }
}
