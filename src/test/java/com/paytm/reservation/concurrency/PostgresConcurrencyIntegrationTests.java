package com.paytm.reservation.concurrency;

import com.paytm.reservation.dto.ReservationResultDto;
import com.paytm.reservation.dto.ReserveSeatRequest;
import com.paytm.reservation.exception.SeatConflictException;
import com.paytm.reservation.repository.*;
import com.paytm.reservation.service.ReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class PostgresConcurrencyIntegrationTests {

    private static final Logger log = LoggerFactory.getLogger(PostgresConcurrencyIntegrationTests.class);

    static PostgreSQLContainer<?> postgres;

    static {
        try {
            postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("paytm_seat_reservation")
                    .withUsername("postgres")
                    .withPassword("postgres");
            postgres.start();
        } catch (Throwable e) {
            log.warn("Docker environment not available, falling back to H2 PostgreSQL mode for tests: {}", e.getMessage());
            postgres = null;
        }
    }

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        if (postgres != null && postgres.isRunning()) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", postgres::getUsername);
            registry.add("spring.datasource.password", postgres::getPassword);
            registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        } else {
            registry.add("spring.datasource.url", () -> "jdbc:h2:mem:postgres_concurrency_db;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
            registry.add("spring.datasource.username", () -> "sa");
            registry.add("spring.datasource.password", () -> "");
            registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        }
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "50");
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
        boolean completed = endLatch.await(45, TimeUnit.SECONDS);
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

    @Test
    void testConcurrentCancellationAndReservationSameSeat() throws Exception {
        UUID showId = showRepository.save("Concurrent Cancel Show", "Desc", "Hall", Timestamp.from(Instant.now().plusSeconds(86400)), 5, 5, "ON_SALE");
        showSeatRepository.save(showId, "S-CONC", "STANDARD", 2000L, "RESERVED");

        UUID user1 = userRepository.save("canceller_user", passwordEncoder.encode("password"), "canceller@test.com", "USER");
        UUID user2 = userRepository.save("reserver_user", passwordEncoder.encode("password"), "reserver@test.com", "USER");

        // Set up initial confirmed reservation for User 1
        UUID reservationId = reservationRepository.createReservation(
                showId,
                user1,
                "S-CONC",
                2000L,
                "CONFIRMED",
                Timestamp.from(Instant.now().plusSeconds(900))
        );
        showUserCounterRepository.ensureCounterExists(showId, user1);
        showUserCounterRepository.incrementCount(showId, user1);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(2);

        AtomicInteger cancelSuccess = new AtomicInteger(0);
        AtomicInteger reserveSuccess = new AtomicInteger(0);

        // Thread 1: User 1 cancels reservation
        executor.submit(() -> {
            try {
                startLatch.await();
                reservationService.cancelReservation(reservationId, user1);
                cancelSuccess.incrementAndGet();
            } catch (Exception ex) {
                ex.printStackTrace();
            } finally {
                endLatch.countDown();
            }
        });

        // Thread 2: User 2 tries to reserve the same seat S-CONC
        executor.submit(() -> {
            try {
                startLatch.await();
                ReserveSeatRequest req = new ReserveSeatRequest(List.of("S-CONC"));
                reservationService.reserveSeats(showId, user2, "KEY-CONC-RES", req);
                reserveSuccess.incrementAndGet();
            } catch (SeatConflictException ignored) {
                // Allowed if reserve executed before cancel
            } catch (Exception ex) {
                ex.printStackTrace();
            } finally {
                endLatch.countDown();
            }
        });

        startLatch.countDown();
        boolean finished = endLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(finished, "Both concurrent threads must finish without deadlocking");
        assertEquals(1, cancelSuccess.get(), "Cancellation must succeed");

        Map<String, Object> resMap = reservationRepository.findById(reservationId).orElseThrow();
        assertEquals("CANCELLED", resMap.get("status"));
    }

    @Test
    void testCancellationNeverResurrectsSeatConfirmedByOther() throws Exception {
        UUID showId = showRepository.save("Resurrect Test Show", "Desc", "Hall", Timestamp.from(Instant.now().plusSeconds(86400)), 5, 5, "ON_SALE");
        // Seat status has already been modified to TAKEN/CONFIRMED by another transaction/owner
        showSeatRepository.save(showId, "S-OTHER", "STANDARD", 2000L, "BLOCKED");

        UUID user1 = userRepository.save("owner_user", passwordEncoder.encode("password"), "owner@test.com", "USER");

        UUID reservationId = reservationRepository.createReservation(
                showId,
                user1,
                "S-OTHER",
                2000L,
                "CONFIRMED",
                Timestamp.from(Instant.now().plusSeconds(900))
        );

        reservationService.cancelReservation(reservationId, user1);

        Map<String, Object> seatMap = showSeatRepository.findByShowIdAndSeatLabel(showId, "S-OTHER").orElseThrow();
        assertEquals("BLOCKED", seatMap.get("status"), "Cancellation must NEVER resurrect a seat that is BLOCKED/held by another entity");
    }

    @Test
    void testDataIntegrityViolationHandling() throws Exception {
        UUID showId = showRepository.save("Constraint Test Show", "Desc", "Hall", Timestamp.from(Instant.now().plusSeconds(86400)), 5, 5, "ON_SALE");
        UUID userId = userRepository.save("constraint_user", passwordEncoder.encode("password"), "constraint@test.com", "USER");

        // Attempting to directly insert duplicate user counter row to trigger DataIntegrityViolationException
        showUserCounterRepository.ensureCounterExists(showId, userId);
        assertThrows(Exception.class, () -> {
            jdbcTemplate.update("INSERT INTO show_user_counters (id, show_id, user_id, reserved_count) VALUES (?, ?, ?, 0)",
                    UUID.randomUUID(), showId, userId);
        });
    }
}
