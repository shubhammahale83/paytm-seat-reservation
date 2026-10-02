package com.paytm.reservation.service;

import com.paytm.reservation.dto.ShowDto;
import com.paytm.reservation.repository.ShowRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ShowService {

    private final ShowRepository showRepository;

    public ShowService(ShowRepository showRepository) {
        this.showRepository = showRepository;
    }

    public ShowDto createShow(ShowDto showDto) {
        String status = showDto.getStatus() != null ? showDto.getStatus() : "UPCOMING";
        int availableSeats = showDto.getAvailableSeats() > 0 ? showDto.getAvailableSeats() : showDto.getTotalSeats();
        UUID id = showRepository.save(
                showDto.getTitle(),
                showDto.getDescription(),
                showDto.getVenue(),
                showDto.getShowTime(),
                showDto.getTotalSeats(),
                availableSeats,
                status
        );
        showDto.setId(id);
        showDto.setAvailableSeats(availableSeats);
        showDto.setStatus(status);
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
