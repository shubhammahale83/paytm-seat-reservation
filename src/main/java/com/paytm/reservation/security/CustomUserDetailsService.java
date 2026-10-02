package com.paytm.reservation.security;

import com.paytm.reservation.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByUsername(username)
                .map(userMap -> new User(
                        (String) userMap.get("username"),
                        (String) userMap.get("password"),
                        Collections.singletonList(new SimpleGrantedAuthority((String) userMap.getOrDefault("role", "ROLE_USER")))
                ))
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username));
    }
}
