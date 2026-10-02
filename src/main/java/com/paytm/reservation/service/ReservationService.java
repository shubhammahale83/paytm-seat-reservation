package com.paytm.reservation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paytm.reservation.dto.ReservationDto;
import com.paytm.reservation.dto.ReservationResultDto;
import com.paytm.reservation.dto.ReserveSeatRequest;
import com.paytm.reservation.exception.ResourceNotFoundException;
import com.paytm.reservation.exception.SeatConflictException;
import com.paytm.reservation.repository.IdempotencyRecordRepository;
import com.paytm.reservation.repository.ReservationRepository;
import com.paytm.reservation.repository.ShowRepository;
import com.paytm.reservation.repository.ShowSeatRepository;
import com.paytm.reservation.repository.ShowUserCounterRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Service
public class ReservationService {

    public static final int MAX_PER_USER_LIMIT = 4;

    private final ShowRepository showRepository;
    private final ShowSeatRepository showSeatRepository;
    private final ShowUserCounterRepository showUserCounterRepository;
    private final ReservationRepository reservationRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final ObjectMapper objectMapper;

    public ReservationService(ShowRepository showRepository,
                              ShowSeatRepository showSeatRepository,
                              ShowUserCounterRepository showUserCounterRepository,
                              ReservationRepository reservationRepository,
                              IdempotencyRecordRepository idempotencyRecordRepository,
                              ObjectMapper objectMapper) {
        this.showRepository = showRepository;
        this.showSeatRepository = showSeatRepository;
        this.showUserCounterRepository = showUserCounterRepository;
        this.reservationRepository = reservationRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReservationResultDto reserveSeats(UUID showId, UUID userId, String idempotencyKey, ReserveSeatRequest request) {
        if (request == null || request.getSeats() == null || request.getSeats().isEmpty()) {
            throw new IllegalArgumentException("Seats list cannot be empty");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key header is required");
        }

        // 1. Normalize and sort requested seat labels
        List<String> sortedSeats = request.getSeats().stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .sorted()
                .toList();

        if (sortedSeats.isEmpty()) {
            throw new IllegalArgumentException("Seats list cannot be empty");
        }

        String requestHash = String.join(",", sortedSeats);

        // 2. Ensure show exists
        Map<String, Object> show = showRepository.findById(showId)
                .orElseThrow(() -> new ResourceNotFoundException("Show", "id", showId));

        // 3. Idempotency Lock using SELECT FOR UPDATE
        idempotencyRecordRepository.ensureRecordExists(showId, userId, idempotencyKey, requestHash);
        Map<String, Object> idempotencyRecord = idempotencyRecordRepository.findAndLockRecord(showId, userId, idempotencyKey)
                .orElseThrow(() -> new IllegalStateException("Idempotency record missing"));

        String existingStatus = (String) idempotencyRecord.get("status");
        String existingHash = (String) idempotencyRecord.get("request_hash");
        String existingPayload = (String) idempotencyRecord.get("response_payload");

        if ("COMPLETED".equals(existingStatus)) {
            if (!requestHash.equals(existingHash)) {
                throw new SeatConflictException("Idempotency key reused with different seat list");
            }
            try {
                return objectMapper.readValue(existingPayload, ReservationResultDto.class);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Error parsing cached idempotency payload", e);
            }
        }

        if (existingHash != null && !existingHash.equals(requestHash)) {
            throw new SeatConflictException("Idempotency key reused with different seat list");
        }

        // 4. Lock User Counter as Serialization Point for Per-User Quota
        showUserCounterRepository.ensureCounterExists(showId, userId);
        Map<String, Object> userCounter = showUserCounterRepository.findAndLockCounter(showId, userId)
                .orElseThrow(() -> new IllegalStateException("User counter missing"));

        int currentReservedCount = ((Number) userCounter.get("reserved_count")).intValue();

        if (currentReservedCount + sortedSeats.size() > MAX_PER_USER_LIMIT) {
            throw new SeatConflictException("Per-user reservation limit exceeded. Current: " + currentReservedCount + ", Requested: " + sortedSeats.size() + ", Max: " + MAX_PER_USER_LIMIT);
        }

        // 5. Lock requested seats in deterministic sorted order using SELECT FOR UPDATE
        long totalPricePaise = 0L;
        List<Map<String, Object>> lockedSeats = new ArrayList<>();

        for (String seatLabel : sortedSeats) {
            Optional<Map<String, Object>> seatOpt = showSeatRepository.findAndLockSeat(showId, seatLabel);
            if (seatOpt.isEmpty()) {
                throw new SeatConflictException("Seat " + seatLabel + " does not exist for this show");
            }
            Map<String, Object> seat = seatOpt.get();
            String status = (String) seat.get("status");

            if (!"AVAILABLE".equals(status)) {
                throw new SeatConflictException("Seat " + seatLabel + " is taken or unavailable");
            }

            totalPricePaise += ((Number) seat.get("price_paise")).longValue();
            lockedSeats.add(seat);
        }

        // 6. Execute state changes (All-or-Nothing)
        List<UUID> reservationIds = new ArrayList<>();
        Timestamp expiresAt = Timestamp.from(Instant.now().plusSeconds(900));

        for (String seatLabel : sortedSeats) {
            showSeatRepository.updateStatus(showId, seatLabel, "RESERVED");
            UUID reservationId = reservationRepository.createReservation(
                    showId,
                    userId,
                    seatLabel,
                    totalPricePaise / sortedSeats.size(),
                    "CONFIRMED",
                    expiresAt
            );
            reservationIds.add(reservationId);
        }

        // Increment user counter
        showUserCounterRepository.incrementCountBy(showId, userId, sortedSeats.size());

        // Decrement available seats on show
        showRepository.decrementAvailableSeats(showId, sortedSeats.size());

        // Construct response
        ReservationResultDto result = new ReservationResultDto(
                showId,
                userId,
                sortedSeats,
                totalPricePaise,
                "CONFIRMED",
                reservationIds
        );

        // Update idempotency record to COMPLETED
        try {
            String payloadJson = objectMapper.writeValueAsString(result);
            idempotencyRecordRepository.updateResponse(showId, userId, idempotencyKey, payloadJson, "COMPLETED");
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize reservation result", e);
        }

        return result;
    }

    @Transactional
    public ReservationDto reserveSeat(UUID userId, UUID seatId) {
        return new ReservationDto(UUID.randomUUID(), seatId, userId, "PENDING", null);
    }

    public List<ReservationDto> getUserReservations(UUID userId) {
        List<Map<String, Object>> reservations = reservationRepository.findByUserId(userId);
        if (reservations.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.emptyList();
    }

    @Transactional
    public void cancelReservation(UUID reservationId, UUID userId) {
        Map<String, Object> reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation", "id", reservationId));

        Object resUserIdObj = reservation.get("user_id");
        UUID resUserId = (resUserIdObj instanceof UUID uuid) ? uuid : UUID.fromString(resUserIdObj.toString());

        if (!resUserId.equals(userId)) {
            throw new AccessDeniedException("Forbidden: You can only cancel your own reservations");
        }

        reservationRepository.updateStatus(reservationId, "CANCELLED");
    }
}
