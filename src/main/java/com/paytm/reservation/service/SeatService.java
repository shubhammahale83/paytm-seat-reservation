package com.paytm.reservation.service;

import com.paytm.reservation.dto.SeatDto;
import com.paytm.reservation.exception.ResourceNotFoundException;
import com.paytm.reservation.repository.SeatRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class SeatService {

    private final SeatRepository seatRepository;

    public SeatService(SeatRepository seatRepository) {
        this.seatRepository = seatRepository;
    }

    public List<SeatDto> getAllAvailableSeats() {
        // Business logic placeholder
        List<Map<String, Object>> seats = seatRepository.findByStatus("AVAILABLE");
        if (seats.isEmpty()) {
            return Collections.emptyList();
        }
        return seats.stream().map(this::mapToSeatDto).toList();
    }

    public SeatDto getSeatById(Long id) {
        // Business logic placeholder
        return seatRepository.findById(id)
                .map(this::mapToSeatDto)
                .orElseThrow(() -> new ResourceNotFoundException("Seat", "id", id));
    }

    private SeatDto mapToSeatDto(Map<String, Object> map) {
        SeatDto dto = new SeatDto();
        dto.setId(((Number) map.get("id")).longValue());
        dto.setSeatNumber((String) map.get("seat_number"));
        dto.setSeatClass((String) map.get("seat_class"));
        dto.setPrice((BigDecimal) map.get("price"));
        dto.setStatus((String) map.get("status"));
        return dto;
    }
}
