package com.paytm.reservation.controller;

import com.paytm.reservation.dto.SeatDto;
import com.paytm.reservation.service.SeatService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/seats")
public class SeatController {

    private final SeatService seatService;

    public SeatController(SeatService seatService) {
        this.seatService = seatService;
    }

    @GetMapping
    public ResponseEntity<List<SeatDto>> getAvailableSeats() {
        List<SeatDto> seats = seatService.getAllAvailableSeats();
        return ResponseEntity.ok(seats);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SeatDto> getSeatById(@PathVariable Long id) {
        SeatDto seat = seatService.getSeatById(id);
        return ResponseEntity.ok(seat);
    }
}
