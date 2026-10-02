package com.paytm.reservation.security;

import com.paytm.reservation.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Map<String, Object> userMap = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username));

        Object idObj = userMap.get("id");
        UUID id = (idObj instanceof UUID uuid) ? uuid : UUID.fromString(idObj.toString());
        String pwd = (String) userMap.get("password");
        String role = (String) userMap.getOrDefault("role", "USER");

        return UserPrincipal.create(id, username, pwd, role);
    }

    public UserDetails loadUserById(UUID id) throws UsernameNotFoundException {
        Map<String, Object> userMap = userRepository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + id));

        String username = (String) userMap.get("username");
        String pwd = (String) userMap.get("password");
        String role = (String) userMap.getOrDefault("role", "USER");

        return UserPrincipal.create(id, username, pwd, role);
    }
}
