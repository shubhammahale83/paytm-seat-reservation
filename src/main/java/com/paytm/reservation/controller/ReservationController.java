package com.paytm.reservation.controller;

import com.paytm.reservation.dto.ReservationDto;
import com.paytm.reservation.security.UserPrincipal;
import com.paytm.reservation.service.ReservationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ReservationDto> reserveSeat(
            @Valid @RequestBody ReservationDto reservationDto,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        UUID userId = currentUser.getId();
        ReservationDto response = reservationService.reserveSeat(userId, reservationDto.getSeatId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<List<ReservationDto>> getUserReservations(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        UUID userId = currentUser.getId();
        List<ReservationDto> reservations = reservationService.getUserReservations(userId);
        return ResponseEntity.ok(reservations);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<Void> cancelReservation(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        UUID userId = currentUser.getId();
        reservationService.cancelReservation(id, userId);
        return ResponseEntity.noContent().build();
    }
}
