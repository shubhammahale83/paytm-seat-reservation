package com.paytm.reservation.service;

import com.paytm.reservation.dto.ShowDetailDto;
import com.paytm.reservation.dto.ShowDto;
import com.paytm.reservation.exception.ResourceNotFoundException;
import com.paytm.reservation.repository.ShowRepository;
import com.paytm.reservation.repository.ShowSeatRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ShowService {

    private final ShowRepository showRepository;
    private final ShowSeatRepository showSeatRepository;

    public ShowService(ShowRepository showRepository, ShowSeatRepository showSeatRepository) {
        this.showRepository = showRepository;
        this.showSeatRepository = showSeatRepository;
    }

    public ShowDetailDto getShowDetails(UUID showId) {
        Map<String, Object> showMap = showRepository.findById(showId)
                .orElseThrow(() -> new ResourceNotFoundException("Show", "id", showId));

        List<Map<String, Object>> seatRows = showSeatRepository.findByShowId(showId);

        int available = 0;
        int confirmed = 0;

        List<Map<String, Object>> seats = new java.util.ArrayList<>();
        for (Map<String, Object> row : seatRows) {
            String status = (String) getCaseInsensitive(row, "status");
            if ("AVAILABLE".equals(status)) {
                available++;
            } else if ("RESERVED".equals(status) || "CONFIRMED".equals(status)) {
                confirmed++;
            }

            Map<String, Object> seatMap = new java.util.LinkedHashMap<>();
            seatMap.put("id", getCaseInsensitive(row, "id"));
            seatMap.put("show_id", getCaseInsensitive(row, "show_id"));
            seatMap.put("seat_label", getCaseInsensitive(row, "seat_label"));
            seatMap.put("seat_tier", getCaseInsensitive(row, "seat_tier"));
            seatMap.put("price_paise", getCaseInsensitive(row, "price_paise"));
            seatMap.put("status", status);
            seats.add(seatMap);
        }

        Number totalSeatsNum = (Number) getCaseInsensitive(showMap, "total_seats");
        int total = seats.size() > 0 ? seats.size() : (totalSeatsNum != null ? totalSeatsNum.intValue() : 0);
        int held = 0; // Explicit cancellation is used rather than time-based holds, so held is always 0.

        ShowDetailDto dto = new ShowDetailDto();
        dto.setId((UUID) getCaseInsensitive(showMap, "id"));
        dto.setTitle((String) getCaseInsensitive(showMap, "title"));
        dto.setDescription((String) getCaseInsensitive(showMap, "description"));
        dto.setVenue((String) getCaseInsensitive(showMap, "venue"));
        Object showTimeObj = getCaseInsensitive(showMap, "show_time");
        if (showTimeObj instanceof java.sql.Timestamp ts) {
            dto.setShowTime(ts);
        } else if (showTimeObj instanceof java.time.OffsetDateTime odt) {
            dto.setShowTime(java.sql.Timestamp.from(odt.toInstant()));
        } else if (showTimeObj instanceof java.time.Instant instant) {
            dto.setShowTime(java.sql.Timestamp.from(instant));
        }
        dto.setStatus((String) getCaseInsensitive(showMap, "status"));
        dto.setTotal(total);
        dto.setAvailable(available);
        dto.setHeld(held);
        dto.setConfirmed(confirmed);
        dto.setSeats(seats);

        return dto;
    }

    private Object getCaseInsensitive(Map<String, Object> map, String key) {
        if (map == null) return null;
        if (map.containsKey(key)) {
            return map.get(key);
        }
        if (map.containsKey(key.toUpperCase())) {
            return map.get(key.toUpperCase());
        }
        if (map.containsKey(key.toLowerCase())) {
            return map.get(key.toLowerCase());
        }
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public ShowDto createShow(ShowDto showDto) {
        String title = showDto.getTitle() != null ? showDto.getTitle() : "Untitled Show";
        String status = showDto.getStatus() != null ? showDto.getStatus() : "ON_SALE";
        long pricePaise = showDto.getPricePaise() != null ? showDto.getPricePaise() : 25000L;

        List<String> seatsList = showDto.getSeats();
        int totalSeats;
        if (seatsList != null && !seatsList.isEmpty()) {
            totalSeats = seatsList.size();
        } else {
            totalSeats = showDto.getTotalSeats() > 0 ? showDto.getTotalSeats() : 100;
            seatsList = new java.util.ArrayList<>();
            for (int i = 1; i <= totalSeats; i++) {
                seatsList.add("S" + i);
            }
        }

        int availableSeats = totalSeats;
        UUID id = showRepository.save(
                title,
                showDto.getDescription() != null ? showDto.getDescription() : "Show Description",
                showDto.getVenue(),
                showDto.getShowTime(),
                totalSeats,
                availableSeats,
                status
        );

        for (String seatLabel : seatsList) {
            showSeatRepository.save(id, seatLabel, "STANDARD", pricePaise, "AVAILABLE");
        }

        showDto.setId(id);
        showDto.setTitle(title);
        showDto.setTotalSeats(totalSeats);
        showDto.setAvailableSeats(availableSeats);
        showDto.setStatus(status);
        showDto.setSeats(seatsList);
        showDto.setPricePaise(pricePaise);
        return showDto;
    }


    public List<ShowDto> getAllShows() {
        List<Map<String, Object>> shows = showRepository.findAll();
        return shows.stream().map(this::mapToShowDto).toList();
    }

    private ShowDto mapToShowDto(Map<String, Object> map) {
        ShowDto dto = new ShowDto();
        dto.setId((UUID) map.get("id"));
        dto.setTitle((String) map.get("title"));
        dto.setDescription((String) map.get("description"));
        dto.setVenue((String) map.get("venue"));
        dto.setShowTime((java.sql.Timestamp) map.get("show_time"));
        dto.setTotalSeats(((Number) map.get("total_seats")).intValue());
        dto.setAvailableSeats(((Number) map.get("available_seats")).intValue());
        dto.setStatus((String) map.get("status"));
        return dto;
    }
}
