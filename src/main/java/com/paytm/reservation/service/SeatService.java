package com.paytm.reservation.service;

import com.paytm.reservation.dto.SeatDto;
import com.paytm.reservation.exception.ResourceNotFoundException;
import com.paytm.reservation.repository.SeatRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SeatService {

    private final SeatRepository seatRepository;

    public SeatService(SeatRepository seatRepository) {
        this.seatRepository = seatRepository;
    }

    public List<SeatDto> getAllAvailableSeats() {
        List<Map<String, Object>> seats = seatRepository.findByStatus("AVAILABLE");
        if (seats.isEmpty()) {
            return Collections.emptyList();
        }
        return seats.stream().map(this::mapToSeatDto).toList();
    }

    public SeatDto getSeatById(UUID id) {
        return seatRepository.findById(id)
                .map(this::mapToSeatDto)
                .orElseThrow(() -> new ResourceNotFoundException("Seat", "id", id));
    }

    private SeatDto mapToSeatDto(Map<String, Object> map) {
        SeatDto dto = new SeatDto();
        dto.setId((UUID) map.get("id"));
        dto.setSeatNumber((String) map.get("seat_label"));
        dto.setSeatClass((String) map.get("seat_tier"));
        Object priceObj = map.get("price_paise");
        if (priceObj instanceof Number number) {
            dto.setPrice(BigDecimal.valueOf(number.longValue(), 2));
        }
        dto.setStatus((String) map.get("status"));
        return dto;
    }
}
