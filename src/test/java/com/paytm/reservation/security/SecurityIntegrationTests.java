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
        showSeatRepository.save(showId, "A1", "STANDARD", 1000L, "AVAILABLE");
        UUID reservationId = reservationRepository.createReservation(
                showId,
                user1Id,
                "A1",
                1000L,
                "PENDING",
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
        showSeatRepository.save(showId, "B1", "STANDARD", 1200L, "AVAILABLE");
        UUID reservationId = reservationRepository.createReservation(
                showId,
                user1Id,
                "B1",
                1200L,
                "PENDING",
                Timestamp.from(Instant.now().plusSeconds(300))
        );

        // User1 cancels User1's reservation
        mockMvc.perform(delete("/api/reservations/" + reservationId)
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isNoContent());
    }
}
