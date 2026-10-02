package com.paytm.reservation.concurrency;

import com.paytm.reservation.dto.ReservationResultDto;
import com.paytm.reservation.dto.ReserveSeatRequest;
import com.paytm.reservation.exception.SeatConflictException;
import com.paytm.reservation.repository.ReservationRepository;
import com.paytm.reservation.repository.ShowRepository;
import com.paytm.reservation.repository.ShowSeatRepository;
import com.paytm.reservation.repository.ShowUserCounterRepository;
import com.paytm.reservation.repository.UserRepository;
import com.paytm.reservation.service.ReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class PostgresConcurrencyIntegrationTests {

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:5432/paytm_seat_reservation");
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private ShowRepository showRepository;

    @Autowired
    private ShowSeatRepository showSeatRepository;

    @Autowired
    private ShowUserCounterRepository showUserCounterRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.update("DELETE FROM reservations");
        jdbcTemplate.update("DELETE FROM idempotency_records");
        jdbcTemplate.update("DELETE FROM show_user_counters");
        jdbcTemplate.update("DELETE FROM show_seats");
        jdbcTemplate.update("DELETE FROM shows");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void test500ConcurrentUsersRacingForOneHotSeat() throws Exception {
        int userCount = 500;
        UUID showId = showRepository.save("Hot Concert", "Desc", "Stadium", Timestamp.from(Instant.now().plusSeconds(86400)), 1, 1, "ON_SALE");
        showSeatRepository.save(showId, "HOT-SEAT-1", "VIP", 50000L, "AVAILABLE");

        List<UUID> userIds = new ArrayList<>();
        String preEncodedPwd = passwordEncoder.encode("password");
        for (int i = 0; i < userCount; i++) {
            UUID uId = userRepository.save("race_user_" + i, preEncodedPwd, "race_user_" + i + "@test.com", "USER");
            userIds.add(uId);
        }

        ExecutorService executor = Executors.newFixedThreadPool(50);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(userCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        for (int i = 0; i < userCount; i++) {
            final UUID uId = userIds.get(i);
            final String idempotencyKey = "IDEMP-RACE-" + i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ReserveSeatRequest req = new ReserveSeatRequest(List.of("HOT-SEAT-1"));
                    reservationService.reserveSeats(showId, uId, idempotencyKey, req);
                    successCount.incrementAndGet();
                } catch (SeatConflictException ex) {
                    conflictCount.incrementAndGet();
                } catch (Exception ex) {
                    ex.printStackTrace();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = endLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "All 500 concurrent threads should finish");
        assertEquals(1, successCount.get(), "Exactly 1 user should succeed in reserving the hot seat");
        assertEquals(499, conflictCount.get(), "Exactly 499 users should receive 409 Conflict");

        Map<String, Object> seat = showSeatRepository.findByShowIdAndSeatLabel(showId, "HOT-SEAT-1").orElseThrow();
        assertEquals("RESERVED", seat.get("status"));
    }

    @Test
    void testSameUserMaking10ConcurrentReservationsWithLimit4() throws Exception {
        int requestCount = 10;
        UUID showId = showRepository.save("User Limit Show", "Desc", "Arena", Timestamp.from(Instant.now().plusSeconds(86400)), 10, 10, "ON_SALE");
        for (int i = 1; i <= 10; i++) {
            showSeatRepository.save(showId, "S" + i, "STANDARD", 1000L, "AVAILABLE");
        }

        UUID userId = userRepository.save("limit_user", passwordEncoder.encode("password"), "limit_user@test.com", "USER");

        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(requestCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        for (int i = 1; i <= requestCount; i++) {
            final String seatLabel = "S" + i;
            final String key = "KEY-USER-LIMIT-" + i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ReserveSeatRequest req = new ReserveSeatRequest(List.of(seatLabel));
                    reservationService.reserveSeats(showId, userId, key, req);
                    successCount.incrementAndGet();
                } catch (SeatConflictException ex) {
                    conflictCount.incrementAndGet();
                } catch (Exception ex) {
                    ex.printStackTrace();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(4, successCount.get(), "Exactly 4 reservations should succeed due to limit 4");
        assertEquals(6, conflictCount.get(), "Exactly 6 requests should fail with 409 Conflict due to limit");

        Map<String, Object> counter = showUserCounterRepository.findByShowIdAndUserId(showId, userId).orElseThrow();
        assertEquals(4, ((Number) counter.get("reserved_count")).intValue(), "Counter in DB must be exactly 4");
    }

    @Test
    void testSameIdempotencyKeyConcurrently() throws Exception {
        int concurrentReqs = 10;
        UUID showId = showRepository.save("Idempotency Show", "Desc", "Theater", Timestamp.from(Instant.now().plusSeconds(86400)), 5, 5, "ON_SALE");
        showSeatRepository.save(showId, "B1", "STANDARD", 2000L, "AVAILABLE");

        UUID userId = userRepository.save("idemp_user", passwordEncoder.encode("password"), "idemp_user@test.com", "USER");
        String sameKey = "CONCURRENT-IDEMP-KEY-999";

        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(concurrentReqs);

        List<ReservationResultDto> results = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger errors = new AtomicInteger(0);

        for (int i = 0; i < concurrentReqs; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ReserveSeatRequest req = new ReserveSeatRequest(List.of("B1"));
                    ReservationResultDto res = reservationService.reserveSeats(showId, userId, sameKey, req);
                    results.add(res);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    errors.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(0, errors.get(), "All concurrent requests with the same idempotency key should succeed");
        assertEquals(10, results.size(), "All 10 requests return a result");

        UUID expectedReservationId = results.get(0).getReservationIds().get(0);
        for (ReservationResultDto res : results) {
            assertEquals(1, res.getReservationIds().size());
            assertEquals(expectedReservationId, res.getReservationIds().get(0), "All responses must return the same reservation ID");
        }

        Map<String, Object> counter = showUserCounterRepository.findByShowIdAndUserId(showId, userId).orElseThrow();
        assertEquals(1, ((Number) counter.get("reserved_count")).intValue(), "Database reserved_count must be exactly 1");
    }

    @Test
    void testSameKeyWithDifferentSeatLists() throws Exception {
        UUID showId = showRepository.save("Mismatch Show", "Desc", "Hall", Timestamp.from(Instant.now().plusSeconds(86400)), 5, 5, "ON_SALE");
        showSeatRepository.save(showId, "C1", "STANDARD", 1500L, "AVAILABLE");
        showSeatRepository.save(showId, "C2", "STANDARD", 1500L, "AVAILABLE");

        UUID userId = userRepository.save("mismatch_user", passwordEncoder.encode("password"), "mismatch_user@test.com", "USER");
        String key = "SAME-KEY-DIFFERENT-SEATS";

        ReserveSeatRequest req1 = new ReserveSeatRequest(List.of("C1"));
        ReservationResultDto res1 = reservationService.reserveSeats(showId, userId, key, req1);
        assertNotNull(res1);

        ReserveSeatRequest req2 = new ReserveSeatRequest(List.of("C2"));
        assertThrows(SeatConflictException.class, () -> {
            reservationService.reserveSeats(showId, userId, key, req2);
        }, "Same key with different seat list must throw SeatConflictException (409)");
    }

    @Test
    void testTwoMultiSeatRequestsWithReversedSeatOrder() throws Exception {
        UUID showId = showRepository.save("Reverse Lock Show", "Desc", "Hall", Timestamp.from(Instant.now().plusSeconds(86400)), 5, 5, "ON_SALE");
        showSeatRepository.save(showId, "X1", "STANDARD", 1000L, "AVAILABLE");
        showSeatRepository.save(showId, "X2", "STANDARD", 1000L, "AVAILABLE");

        UUID user1 = userRepository.save("rev_user1", passwordEncoder.encode("password"), "rev_user1@test.com", "USER");
        UUID user2 = userRepository.save("rev_user2", passwordEncoder.encode("password"), "rev_user2@test.com", "USER");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(2);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        executor.submit(() -> {
            try {
                startLatch.await();
                ReserveSeatRequest req = new ReserveSeatRequest(List.of("X1", "X2"));
                reservationService.reserveSeats(showId, user1, "KEY-REV-1", req);
                successCount.incrementAndGet();
            } catch (SeatConflictException ex) {
                conflictCount.incrementAndGet();
            } catch (Exception ex) {
                ex.printStackTrace();
            } finally {
                endLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                ReserveSeatRequest req = new ReserveSeatRequest(List.of("X2", "X1"));
                reservationService.reserveSeats(showId, user2, "KEY-REV-2", req);
                successCount.incrementAndGet();
            } catch (SeatConflictException ex) {
                conflictCount.incrementAndGet();
            } catch (Exception ex) {
                ex.printStackTrace();
            } finally {
                endLatch.countDown();
            }
        });

        startLatch.countDown();
        endLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(1, successCount.get(), "No deadlock! Exactly 1 multi-seat request succeeds");
        assertEquals(1, conflictCount.get(), "The other multi-seat request receives 409 Conflict");
    }

    @Test
    void testAllOrNothingPartialRequest() throws Exception {
        UUID showId = showRepository.save("All Or Nothing Show", "Desc", "Hall", Timestamp.from(Instant.now().plusSeconds(86400)), 5, 5, "ON_SALE");
        showSeatRepository.save(showId, "P1", "STANDARD", 1000L, "AVAILABLE");
        showSeatRepository.save(showId, "P2", "STANDARD", 1000L, "RESERVED");

        UUID userId = userRepository.save("aon_user", passwordEncoder.encode("password"), "aon_user@test.com", "USER");

        ReserveSeatRequest req = new ReserveSeatRequest(List.of("P1", "P2"));

        assertThrows(SeatConflictException.class, () -> {
            reservationService.reserveSeats(showId, userId, "AON-KEY-1", req);
        });

        Map<String, Object> seatP1 = showSeatRepository.findByShowIdAndSeatLabel(showId, "P1").orElseThrow();
        assertEquals("AVAILABLE", seatP1.get("status"), "Seat P1 must remain AVAILABLE after failed multi-seat reservation (all-or-nothing)");
    }
}
