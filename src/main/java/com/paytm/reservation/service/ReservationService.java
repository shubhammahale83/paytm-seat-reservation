package com.paytm.reservation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paytm.reservation.dto.ReservationDto;
import com.paytm.reservation.dto.ReservationResultDto;
import com.paytm.reservation.dto.ReserveSeatRequest;
import com.paytm.reservation.exception.ResourceNotFoundException;
import com.paytm.reservation.exception.SeatConflictException;
import com.paytm.reservation.exception.SeatReservationException;
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

import com.paytm.reservation.metrics.ReservationMetrics;
import java.util.concurrent.TimeUnit;

@Service
public class ReservationService {

    public static final int MAX_PER_USER_LIMIT = 4;

    private final ShowRepository showRepository;
    private final ShowSeatRepository showSeatRepository;
    private final ShowUserCounterRepository showUserCounterRepository;
    private final ReservationRepository reservationRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final ObjectMapper objectMapper;
    private final ReservationMetrics reservationMetrics;

    public ReservationService(ShowRepository showRepository,
                              ShowSeatRepository showSeatRepository,
                              ShowUserCounterRepository showUserCounterRepository,
                              ReservationRepository reservationRepository,
                              IdempotencyRecordRepository idempotencyRecordRepository,
                              ObjectMapper objectMapper,
                              ReservationMetrics reservationMetrics) {
        this.showRepository = showRepository;
        this.showSeatRepository = showSeatRepository;
        this.showUserCounterRepository = showUserCounterRepository;
        this.reservationRepository = reservationRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.objectMapper = objectMapper;
        this.reservationMetrics = reservationMetrics;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReservationResultDto reserveSeats(UUID showId, UUID userId, String idempotencyKey, ReserveSeatRequest request) {
        long startNanos = System.nanoTime();
        try {
            return doReserveSeats(showId, userId, idempotencyKey, request);
        } finally {
            reservationMetrics.getReservationTimer().record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
        }
    }

    private ReservationResultDto doReserveSeats(UUID showId, UUID userId, String idempotencyKey, ReserveSeatRequest request) {
        if (request == null || request.getSeats() == null || request.getSeats().isEmpty()) {
            throw new IllegalArgumentException("Seats list cannot be empty");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key header is required");
        }

        // 1. Normalize and sort requested seat labels
        List<String> sortedSeats = request.getSeats().stream()
                .filter(Objects::nonNull)
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

        // 3. Idempotency Lock using SELECT FOR UPDATE with retry
        Map<String, Object> idempotencyRecord = null;
        for (int i = 0; i < 3; i++) {
            idempotencyRecordRepository.ensureRecordExists(showId, userId, idempotencyKey, requestHash);
            Optional<Map<String, Object>> recOpt = idempotencyRecordRepository.findAndLockRecord(showId, userId, idempotencyKey);
            if (recOpt.isPresent()) {
                idempotencyRecord = recOpt.get();
                break;
            }
        }
        if (idempotencyRecord == null) {
            throw new SeatConflictException("Could not acquire idempotency record lock");
        }

        String existingStatus = (String) idempotencyRecord.get("status");
        String existingHash = (String) idempotencyRecord.get("request_hash");
        String existingPayload = (String) idempotencyRecord.get("response_payload");

        if ("COMPLETED".equals(existingStatus)) {
            if (!requestHash.equals(existingHash)) {
                reservationMetrics.recordDeclinedIdempotencyKeyReuse();
                throw new SeatConflictException("Idempotency key reused with different seat list");
            }
            try {
                reservationMetrics.recordDeclinedIdempotentReplay();
                return objectMapper.readValue(existingPayload, ReservationResultDto.class);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Error parsing cached idempotency payload", e);
            }
        }

        if (existingHash != null && !existingHash.equals(requestHash)) {
            reservationMetrics.recordDeclinedIdempotencyKeyReuse();
            throw new SeatConflictException("Idempotency key reused with different seat list");
        }

        // 4. Lock User Counter as Serialization Point for Per-User Quota
        Map<String, Object> userCounter = null;
        for (int i = 0; i < 3; i++) {
            showUserCounterRepository.ensureCounterExists(showId, userId);
            Optional<Map<String, Object>> counterOpt = showUserCounterRepository.findAndLockCounter(showId, userId);
            if (counterOpt.isPresent()) {
                userCounter = counterOpt.get();
                break;
            }
        }
        if (userCounter == null) {
            throw new SeatConflictException("Could not acquire user counter lock");
        }

        int currentReservedCount = ((Number) userCounter.get("reserved_count")).intValue();

        if (currentReservedCount + sortedSeats.size() > MAX_PER_USER_LIMIT) {
            reservationMetrics.recordDeclinedPerUserLimit();
            throw new SeatConflictException("Per-user reservation limit exceeded. Current: " + currentReservedCount + ", Requested: " + sortedSeats.size() + ", Max: " + MAX_PER_USER_LIMIT);
        }

        // 5. Lock requested seats in deterministic sorted order using SELECT FOR UPDATE
        long totalPricePaise = 0L;
        List<Map<String, Object>> lockedSeats = new ArrayList<>();

        for (String seatLabel : sortedSeats) {
            Optional<Map<String, Object>> seatOpt = showSeatRepository.findAndLockSeat(showId, seatLabel);
            if (seatOpt.isEmpty()) {
                reservationMetrics.recordDeclinedSeatTaken();
                throw new SeatConflictException("SEAT_TAKEN: Seat " + seatLabel + " does not exist for this show");
            }
            Map<String, Object> seat = seatOpt.get();
            String status = (String) seat.get("status");

            if (!"AVAILABLE".equals(status)) {
                reservationMetrics.recordDeclinedSeatTaken();
                throw new SeatConflictException("SEAT_TAKEN: Seat " + seatLabel + " is taken or unavailable");
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

        reservationMetrics.recordConfirmed();
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

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void cancelReservation(UUID reservationId, UUID userId) {
        // 1. Lock reservation row using SELECT FOR UPDATE
        Map<String, Object> reservation = reservationRepository.findAndLockById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation", "id", reservationId));

        // 2. Validate authenticated user is reservation owner
        Object resUserIdObj = reservation.get("user_id");
        UUID resUserId = (resUserIdObj instanceof UUID uuid) ? uuid : UUID.fromString(resUserIdObj.toString());

        if (!resUserId.equals(userId)) {
            throw new AccessDeniedException("Forbidden: You can only cancel your own reservations");
        }

        // 3. Ensure only CONFIRMED reservations can be cancelled
        String reservationStatus = (String) reservation.get("status");
        if (!"CONFIRMED".equals(reservationStatus)) {
            throw new SeatReservationException("Only CONFIRMED reservations can be cancelled. Current status: " + reservationStatus);
        }

        Object showIdObj = reservation.get("show_id");
        UUID showId = (showIdObj instanceof UUID uuid) ? uuid : UUID.fromString(showIdObj.toString());
        String seatLabel = (String) reservation.get("seat_label");

        // 4. Lock seat rows in deterministic order
        List<String> sortedSeats = List.of(seatLabel).stream().sorted().toList();
        for (String seat : sortedSeats) {
            Optional<Map<String, Object>> seatOpt = showSeatRepository.findAndLockSeat(showId, seat);
            if (seatOpt.isPresent()) {
                Map<String, Object> seatMap = seatOpt.get();
                String currentSeatStatus = (String) seatMap.get("status");

                // Cancellation must never resurrect a seat that another transaction has already confirmed/changed
                if ("CONFIRMED".equals(currentSeatStatus) || "RESERVED".equals(currentSeatStatus)) {
                    showSeatRepository.updateStatus(showId, seat, "AVAILABLE");
                    showRepository.incrementAvailableSeats(showId, 1);
                    showUserCounterRepository.decrementCount(showId, userId);
                }
            }
        }

        // 5. Update reservation status to CANCELLED
        reservationRepository.updateStatus(reservationId, "CANCELLED");
    }
}
