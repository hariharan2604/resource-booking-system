package com.booking.config;

import com.booking.entity.Resource;
import com.booking.entity.Role;
import com.booking.entity.User;
import com.booking.repository.ResourceRepository;
import com.booking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Seeds a couple of test accounts and sample resources on first run so the API is
 * exercisable immediately. Idempotent — checks existence before inserting, safe to
 * run on every startup regardless of ddl-auto mode.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final ResourceRepository resourceRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        seedUser("admin", "admin123","admin@gmail.com", Role.ADMIN);
        seedUser("user", "user123","user@gmail.com", Role.USER);
        seedUser("alice", "alice123", "alice@gmail.com",Role.USER);

        if (resourceRepository.count() == 0) {
            resourceRepository.save(Resource.builder()
                    .name("Conference Room A")
                    .type("ROOM")
                    .description("10-seat conference room with projector and whiteboard")
                    .location("Floor 3, East Wing")
                    .capacity(10)
                    .pricePerHour(new BigDecimal("15.00"))
                    .available(true)
                    .build());

            resourceRepository.save(Resource.builder()
                    .name("Tesla Model 3")
                    .type("VEHICLE")
                    .description("Company electric vehicle for local trips")
                    .location("Parking Garage B")
                    .capacity(5)
                    .pricePerHour(new BigDecimal("25.00"))
                    .available(true)
                    .build());

            resourceRepository.save(Resource.builder()
                    .name("4K Projector Kit")
                    .type("EQUIPMENT")
                    .description("Portable projector with screen and HDMI cables")
                    .location("Equipment Room, Floor 1")
                    .capacity(null)
                    .pricePerHour(new BigDecimal("8.50"))
                    .available(true)
                    .build());

            log.info("Seeded 3 sample resources");
        }
    }

    private void seedUser(String username, String rawPassword,String email, Role role) {
        if (!userRepository.existsByUsername(username)) {
            userRepository.save(User.builder()
                    .username(username)
                    .password(passwordEncoder.encode(rawPassword))
                            .email(email)
                    .role(role)
                    .enabled(true)
                    .build());
            log.info("Seeded {} user '{}'", role, username);
        }
    }
}
