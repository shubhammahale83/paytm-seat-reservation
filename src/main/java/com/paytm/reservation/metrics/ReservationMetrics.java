package com.paytm.reservation.metrics;

import com.paytm.reservation.repository.ShowRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class ReservationMetrics {

    private final MeterRegistry meterRegistry;
    private final Timer reservationTimer;

    public ReservationMetrics(MeterRegistry meterRegistry, ShowRepository showRepository) {
        this.meterRegistry = meterRegistry;

        // Dynamic gauge for seats_available (no user_id, seat, or reservation_id labels)
        Gauge.builder("seats_available", showRepository, repo -> repo.getTotalAvailableSeats())
                .description("Total available seats across all shows")
                .register(meterRegistry);

        // Timer for reservation_duration_seconds
        this.reservationTimer = Timer.builder("reservation_duration_seconds")
                .description("Reservation operation duration in seconds")
                .register(meterRegistry);
    }

    public void recordConfirmed() {
        meterRegistry.counter("reservations_total", "outcome", "confirmed").increment();
    }

    public void recordDeclinedSeatTaken() {
        meterRegistry.counter("reservations_total", "outcome", "declined", "reason", "seat-taken").increment();
    }

    public void recordDeclinedPerUserLimit() {
        meterRegistry.counter("reservations_total", "outcome", "declined", "reason", "per-user-limit").increment();
    }

    public void recordDeclinedIdempotentReplay() {
        meterRegistry.counter("reservations_total", "outcome", "declined", "reason", "idempotent-replay").increment();
    }

    public void recordDeclinedIdempotencyKeyReuse() {
        meterRegistry.counter("reservations_total", "outcome", "declined", "reason", "idempotency-key-reuse").increment();
    }

    public Timer getReservationTimer() {
        return reservationTimer;
    }
}
