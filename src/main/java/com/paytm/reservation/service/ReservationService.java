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

@Service
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final SeatRepository seatRepository;

    public ReservationService(ReservationRepository reservationRepository, SeatRepository seatRepository) {
        this.reservationRepository = reservationRepository;
        this.seatRepository = seatRepository;
    }

    @Transactional
    public ReservationDto reserveSeat(Long userId, Long seatId) {
        // Business logic placeholder
        return new ReservationDto(1L, seatId, userId, "CONFIRMED", null);
    }

    public List<ReservationDto> getUserReservations(Long userId) {
        // Business logic placeholder
        List<Map<String, Object>> reservations = reservationRepository.findByUserId(userId);
        if (reservations.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.emptyList();
    }

    @Transactional
    public void cancelReservation(Long reservationId, Long userId) {
        // Business logic placeholder
        reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation", "id", reservationId));
    }
}
