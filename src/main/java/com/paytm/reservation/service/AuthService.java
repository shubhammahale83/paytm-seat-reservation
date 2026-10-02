package com.paytm.reservation.service;

import com.paytm.reservation.dto.AuthRequest;
import com.paytm.reservation.dto.AuthResponse;
import com.paytm.reservation.repository.UserRepository;
import com.paytm.reservation.security.JwtTokenProvider;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtTokenProvider tokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.tokenProvider = tokenProvider;
    }

    public AuthResponse login(AuthRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );
        String token = tokenProvider.generateToken(authentication);
        return new AuthResponse(token, request.getUsername());
    }

    public void registerUser(AuthRequest request) {
        String encodedPassword = passwordEncoder.encode(request.getPassword());
        String role = (request.getRole() != null && !request.getRole().isBlank()) ? request.getRole() : "USER";
        userRepository.save(request.getUsername(), encodedPassword, request.getUsername() + "@paytm.com", role);
    }
}
