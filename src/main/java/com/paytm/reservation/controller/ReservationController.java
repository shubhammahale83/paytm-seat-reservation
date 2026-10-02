package com.paytm.reservation.controller;

import com.paytm.reservation.dto.ReservationDto;
import com.paytm.reservation.service.ReservationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    public ResponseEntity<ReservationDto> reserveSeat(@Valid @RequestBody ReservationDto reservationDto) {
        UUID userId = UUID.randomUUID();
        ReservationDto response = reservationService.reserveSeat(userId, reservationDto.getSeatId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<ReservationDto>> getUserReservations() {
        UUID userId = UUID.randomUUID();
        List<ReservationDto> reservations = reservationService.getUserReservations(userId);
        return ResponseEntity.ok(reservations);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelReservation(@PathVariable UUID id) {
        UUID userId = UUID.randomUUID();
        reservationService.cancelReservation(id, userId);
        return ResponseEntity.noContent().build();
    }
}
