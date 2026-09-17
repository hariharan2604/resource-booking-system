package com.booking.service;

import com.booking.dto.LoginRequest;
import com.booking.dto.LoginResponse;
import com.booking.dto.RegisterRequest;
import com.booking.dto.UserResponse;
import com.booking.dto.AdminUserCreateRequest;
import com.booking.entity.Role;
import com.booking.entity.User;
import com.booking.exception.InvalidReservationException;
import com.booking.repository.UserRepository;
import com.booking.security.JwtUtil;
import com.booking.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
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

        String token = jwtUtil.generateToken(principal, authorities);

        String role = authorities.get(0).getAuthority().replace("ROLE_", "");

        return LoginResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .username(principal.getUsername())
                .userId(principal.getId())
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
