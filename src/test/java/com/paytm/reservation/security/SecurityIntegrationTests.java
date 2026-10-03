package com.paytm.reservation.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paytm.reservation.dto.ReservationDto;
import com.paytm.reservation.dto.ShowDto;
import com.paytm.reservation.repository.ReservationRepository;
import com.paytm.reservation.repository.ShowRepository;
import com.paytm.reservation.repository.ShowSeatRepository;
import com.paytm.reservation.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class SecurityIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ShowRepository showRepository;

    @Autowired
    private ShowSeatRepository showSeatRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID user1Id;
    private UUID user2Id;
    private UUID adminId;
    private String user1Token;
    private String user2Token;
    private String adminToken;

    @BeforeEach
    void setUp() {
        String encodedPassword = passwordEncoder.encode("password123");
        user1Id = userRepository.save("user1", encodedPassword, "user1@test.com", "USER");
        user2Id = userRepository.save("user2", encodedPassword, "user2@test.com", "USER");
        adminId = userRepository.save("admin1", encodedPassword, "admin1@test.com", "ADMIN");

        user1Token = tokenProvider.generateToken(user1Id, "USER");
        user2Token = tokenProvider.generateToken(user2Id, "USER");
        adminToken = tokenProvider.generateToken(adminId, "ADMIN");
    }

    @Test
    void whenMissingToken_thenReturns401() throws Exception {
        mockMvc.perform(get("/api/reservations"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void whenInvalidToken_thenReturns401() throws Exception {
        mockMvc.perform(get("/api/reservations")
                        .header("Authorization", "Bearer invalid_token_12345"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void whenUserTriesToCreateShow_thenReturns403() throws Exception {
        ShowDto showDto = new ShowDto();
        showDto.setTitle("Test Movie");
        showDto.setVenue("Auditorium 1");
        showDto.setShowTime(Timestamp.from(Instant.now().plusSeconds(86400)));
        showDto.setTotalSeats(100);

        mockMvc.perform(post("/api/shows")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(showDto)))
                .andExpect(status().isForbidden());
    }

    @Test
    void whenAdminCreatesShow_thenReturns201() throws Exception {
        ShowDto showDto = new ShowDto();
        showDto.setTitle("Admin Movie");
        showDto.setVenue("Auditorium 2");
        showDto.setShowTime(Timestamp.from(Instant.now().plusSeconds(86400)));
        showDto.setTotalSeats(50);

        mockMvc.perform(post("/api/shows")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(showDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.title").value("Admin Movie"));
    }

    @Test
    void whenUserReservesSeat_thenUsesJwtUserId() throws Exception {
        UUID seatId = UUID.randomUUID();
        ReservationDto dto = new ReservationDto();
        dto.setSeatId(seatId);
        dto.setUserId(user2Id); // Impersonation attempt

        String responseJson = mockMvc.perform(post("/api/reservations")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        ReservationDto result = objectMapper.readValue(responseJson, ReservationDto.class);
        assertEquals(user1Id, result.getUserId(), "User ID must come from JWT, ignoring body JSON");
    }

    @Test
    void whenUserCancelsOthersReservation_thenReturns403() throws Exception {
        UUID showId = showRepository.save("Show 1", "Desc", "Venue 1", Timestamp.from(Instant.now().plusSeconds(3600)), 100, 100, "UPCOMING");
        showSeatRepository.save(showId, "A1", "STANDARD", 1000L, "RESERVED");
        UUID reservationId = reservationRepository.createReservation(
                showId,
                user1Id,
                "A1",
                1000L,
                "CONFIRMED",
                Timestamp.from(Instant.now().plusSeconds(300))
        );

        // User2 attempts to cancel User1's reservation
        mockMvc.perform(delete("/api/reservations/" + reservationId)
                        .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isForbidden());
    }

    @Test
    void whenUserCancelsOwnReservation_thenReturns204() throws Exception {
        UUID showId = showRepository.save("Show 2", "Desc", "Venue 2", Timestamp.from(Instant.now().plusSeconds(3600)), 100, 100, "UPCOMING");
        showSeatRepository.save(showId, "B1", "STANDARD", 1200L, "RESERVED");
        UUID reservationId = reservationRepository.createReservation(
                showId,
                user1Id,
                "B1",
                1200L,
                "CONFIRMED",
                Timestamp.from(Instant.now().plusSeconds(300))
        );

        // User1 cancels User1's reservation
        mockMvc.perform(delete("/api/reservations/" + reservationId)
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isNoContent());
    }

    @Test
    void whenReserveSeatsViaShowsEndpoint_thenReturns201() throws Exception {
        UUID showId = showRepository.save("Show 3", "Desc", "Venue 3", Timestamp.from(Instant.now().plusSeconds(3600)), 100, 100, "ON_SALE");
        showSeatRepository.save(showId, "A1", "STANDARD", 1500L, "AVAILABLE");
        showSeatRepository.save(showId, "A2", "STANDARD", 1500L, "AVAILABLE");

        String requestBody = "{\"seats\":[\"A1\", \"A2\"]}";

        mockMvc.perform(post("/shows/" + showId + "/reserve")
                        .header("Authorization", "Bearer " + user1Token)
                        .header("Idempotency-Key", "TEST-KEY-100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.showId").value(showId.toString()))
                .andExpect(jsonPath("$.userId").value(user1Id.toString()))
                .andExpect(jsonPath("$.seats[0]").value("A1"))
                .andExpect(jsonPath("$.seats[1]").value("A2"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void whenReserveSeatsTaken_thenReturns409() throws Exception {
        UUID showId = showRepository.save("Show 4", "Desc", "Venue 4", Timestamp.from(Instant.now().plusSeconds(3600)), 100, 100, "ON_SALE");
        showSeatRepository.save(showId, "A1", "STANDARD", 1500L, "RESERVED");

        String requestBody = "{\"seats\":[\"A1\"]}";

        mockMvc.perform(post("/shows/" + showId + "/reserve")
                        .header("Authorization", "Bearer " + user1Token)
                        .header("Idempotency-Key", "TEST-KEY-101")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict());
    }

    @Test
    void whenCancelConfirmedReservationViaPost_thenReturns200() throws Exception {
        UUID showId = showRepository.save("Show 5", "Desc", "Venue 5", Timestamp.from(Instant.now().plusSeconds(3600)), 100, 100, "ON_SALE");
        showSeatRepository.save(showId, "C1", "STANDARD", 1000L, "RESERVED");
        UUID reservationId = reservationRepository.createReservation(
                showId,
                user1Id,
                "C1",
                1000L,
                "CONFIRMED",
                Timestamp.from(Instant.now().plusSeconds(300))
        );

        mockMvc.perform(post("/reservations/" + reservationId + "/cancel")
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isOk());
    }

    @Test
    void whenCancelOthersConfirmedReservationViaPost_thenReturns403() throws Exception {
        UUID showId = showRepository.save("Show 6", "Desc", "Venue 6", Timestamp.from(Instant.now().plusSeconds(3600)), 100, 100, "ON_SALE");
        showSeatRepository.save(showId, "C2", "STANDARD", 1000L, "RESERVED");
        UUID reservationId = reservationRepository.createReservation(
                showId,
                user1Id,
                "C2",
                1000L,
                "CONFIRMED",
                Timestamp.from(Instant.now().plusSeconds(300))
        );

        // User2 attempts to cancel User1's reservation
        mockMvc.perform(post("/reservations/" + reservationId + "/cancel")
                        .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isForbidden());
    }

    @Test
    void whenCancelUnconfirmedReservationViaPost_thenReturns400() throws Exception {
        UUID showId = showRepository.save("Show 7", "Desc", "Venue 7", Timestamp.from(Instant.now().plusSeconds(3600)), 100, 100, "ON_SALE");
        showSeatRepository.save(showId, "C3", "STANDARD", 1000L, "AVAILABLE");
        UUID reservationId = reservationRepository.createReservation(
                showId,
                user1Id,
                "C3",
                1000L,
                "CANCELLED",
                Timestamp.from(Instant.now().plusSeconds(300))
        );

        mockMvc.perform(post("/reservations/" + reservationId + "/cancel")
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void whenGetShowById_thenReturnsShowDetailsWithSeatCounts() throws Exception {
        UUID showId = showRepository.save("Show Detail Test", "Desc", "Auditorium 3", Timestamp.from(Instant.now().plusSeconds(3600)), 2, 1, "ON_SALE");
        showSeatRepository.save(showId, "D1", "STANDARD", 1000L, "AVAILABLE");
        showSeatRepository.save(showId, "D2", "STANDARD", 1000L, "RESERVED");

        mockMvc.perform(get("/shows/" + showId))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-ID"))
                .andExpect(jsonPath("$.id").value(showId.toString()))
                .andExpect(jsonPath("$.title").value("Show Detail Test"))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.available").value(1))
                .andExpect(jsonPath("$.held").value(0))
                .andExpect(jsonPath("$.confirmed").value(1))
                .andExpect(jsonPath("$.seats[0].seat_label").value("D1"))
                .andExpect(jsonPath("$.seats[1].seat_label").value("D2"));
    }

    @Test
    void testLivenessAndReadinessProbes() throws Exception {
        mockMvc.perform(get("/livez"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-ID"))
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/readyz"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-ID"))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.database").value("UP"));
    }

    @Test
    void testPrometheusActuatorEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("seats_available")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("reservations_total")));
    }
}
