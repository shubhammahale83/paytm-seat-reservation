package com.paytm.reservation.controller;

import com.paytm.reservation.dto.ReservationDto;
import com.paytm.reservation.service.ReservationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    public ResponseEntity<ReservationDto> reserveSeat(@Valid @RequestBody ReservationDto reservationDto) {
        // Dummy user id for skeleton setup
        Long userId = 1L;
        ReservationDto response = reservationService.reserveSeat(userId, reservationDto.getSeatId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<ReservationDto>> getUserReservations() {
        Long userId = 1L;
        List<ReservationDto> reservations = reservationService.getUserReservations(userId);
        return ResponseEntity.ok(reservations);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelReservation(@PathVariable Long id) {
        Long userId = 1L;
        reservationService.cancelReservation(id, userId);
        return ResponseEntity.noContent().build();
    }
}
