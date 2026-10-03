package com.paytm.reservation.controller;

import com.paytm.reservation.dto.ReservationResultDto;
import com.paytm.reservation.dto.ReserveSeatRequest;
import com.paytm.reservation.dto.ShowDto;
import com.paytm.reservation.security.UserPrincipal;
import com.paytm.reservation.service.ReservationService;
import com.paytm.reservation.service.ShowService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping({"/api/shows", "/shows"})
public class ShowController {

    private final ShowService showService;
    private final ReservationService reservationService;

    public ShowController(ShowService showService, ReservationService reservationService) {
        this.showService = showService;
        this.reservationService = reservationService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ShowDto> createShow(@Valid @RequestBody ShowDto showDto) {
        ShowDto created = showService.createShow(showDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<ShowDto>> getAllShows() {
        return ResponseEntity.ok(showService.getAllShows());
    }

    @PostMapping("/{showId}/reserve")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ReservationResultDto> reserveSeats(
            @PathVariable UUID showId,
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
            @Valid @RequestBody ReserveSeatRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        UUID userId = currentUser.getId();
        ReservationResultDto result = reservationService.reserveSeats(showId, userId, idempotencyKey, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }
}
