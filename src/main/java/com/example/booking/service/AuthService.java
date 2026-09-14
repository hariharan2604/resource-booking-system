package com.example.booking.service;

import com.example.booking.dto.LoginRequest;
import com.example.booking.dto.LoginResponse;
import com.example.booking.dto.RegisterRequest;
import com.example.booking.dto.UserResponse;
import com.example.booking.dto.AdminUserCreateRequest;
import com.example.booking.entity.Role;
import com.example.booking.entity.User;
import com.example.booking.exception.InvalidReservationException;
import com.example.booking.repository.UserRepository;
import com.example.booking.security.JwtUtil;
import com.example.booking.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j 
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    public LoginResponse login(LoginRequest request) {
        // Delegates to the DaoAuthenticationProvider configured in SecurityConfig,
        // which loads the user via CustomUserDetailsService and checks the BCrypt hash.
        // Throws BadCredentialsException on mismatch, handled by
        // GlobalExceptionHandler.
//        Authentication authentication = authenticationManager.authenticate(
//                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.getUsername(),request.getPassword()));

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        @SuppressWarnings("unchecked")
        List<? extends GrantedAuthority> authorities = (List<? extends GrantedAuthority>) authentication
                .getAuthorities();

        String token = jwtUtil.generateToken(principal.getUsername(), principal.getId(), authorities);

        String role = authorities.get(0).getAuthority().replace("ROLE_", "");

        return LoginResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .username(principal.getUsername())
                .role(role)
                .expiresInMs(expirationMs)
                .build();
    }

    public UserResponse register(RegisterRequest request) {
        return createUser(request.getUsername(), request.getPassword(), Role.USER);
    }

    public UserResponse createUser(AdminUserCreateRequest request) {
        return createUser(request.getUsername(), request.getPassword(), request.getRole());
    }

    private UserResponse createUser(String username, String password, Role role) {
        if (userRepository.existsByUsername(username)) {
            throw new InvalidReservationException("Username is already registered");
        }
        User user = userRepository.save(User.builder()
                .username(username)
                .password(passwordEncoder.encode(password))
                .role(role)
                .enabled(true)
                .build());
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .role(user.getRole())
                .enabled(user.isEnabled())
                .build();
    }
}
