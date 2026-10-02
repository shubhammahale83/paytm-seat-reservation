package com.paytm.reservation.service;

import com.paytm.reservation.dto.ReservationDto;
import com.paytm.reservation.exception.ResourceNotFoundException;
import com.paytm.reservation.repository.ReservationRepository;
import com.paytm.reservation.repository.SeatRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final SeatRepository seatRepository;

    public ReservationService(ReservationRepository reservationRepository, SeatRepository seatRepository) {
        this.reservationRepository = reservationRepository;
        this.seatRepository = seatRepository;
    }

    @Transactional
    public ReservationDto reserveSeat(UUID userId, UUID seatId) {
        // Business logic placeholder
        return new ReservationDto(UUID.randomUUID(), seatId, userId, "PENDING", null);
    }

    public List<ReservationDto> getUserReservations(UUID userId) {
        List<Map<String, Object>> reservations = reservationRepository.findByUserId(userId);
        if (reservations.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.emptyList();
    }

    @Transactional
    public void cancelReservation(UUID reservationId, UUID userId) {
        reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation", "id", reservationId));
    }
}
