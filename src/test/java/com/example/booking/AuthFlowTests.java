package com.example.booking;

import com.example.booking.entity.Role;
import com.example.booking.entity.User;
import com.example.booking.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        if (!userRepository.existsByUsername("testadmin")) {
            userRepository.save(User.builder()
                    .username("testadmin")
                    .password(passwordEncoder.encode("password"))
                    .role(Role.ADMIN)
                    .enabled(true)
                    .build());
        }
    }

    @Test
    void loginWithValidCredentials_returnsJwt() throws Exception {
        mockMvc.perform(post("/auth/login")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of(
                        "username", "testadmin",
                        "password", "password"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void loginWithInvalidCredentials_returns401() throws Exception {
        mockMvc.perform(post("/auth/login")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of(
                        "username", "testadmin",
                        "password", "wrong-password"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticatedRequestToProtectedEndpoint_returns401() throws Exception {
        mockMvc.perform(get("/api/resources"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenForDisabledUser_returns401() throws Exception {
        String username = "disabled-" + System.nanoTime();
        userRepository.save(User.builder()
                .username(username)
                .password(passwordEncoder.encode("password"))
                .role(Role.USER)
                .enabled(true)
                .build());

        String token = mockMvc.perform(post("/auth/login")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of(
                        "username", username,
                        "password", "password"))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String jwt = objectMapper.readTree(token).get("token").asText();
        User user = userRepository.findByUsername(username).orElseThrow();
        user.setEnabled(false);
        userRepository.save(user);

        mockMvc.perform(get("/api/resources")
                .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publicRegistrationCreatesUserAndAdminCanCreateAdmin() throws Exception {
        String username = "registered-" + System.nanoTime();

        mockMvc.perform(post("/auth/register")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of(
                        "username", username,
                        "password", "password123"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.role").value("USER"));

        String adminToken = mockMvc.perform(post("/auth/login")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of(
                        "username", "testadmin",
                        "password", "password"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String jwt = objectMapper.readTree(adminToken).get("token").asText();

        mockMvc.perform(post("/api/users")
                .header("Authorization", "Bearer " + jwt)
                .contentType("application/json")
                .content("{\"username\":\"admin-created-" + System.nanoTime()
                        + "\",\"password\":\"password123\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void regularUserCannotCreateAdminAccount() throws Exception {
        mockMvc.perform(post("/api/users")
                .header("Authorization", "Bearer " + loginToken("user", "user123"))
                .contentType("application/json")
                .content("{\"username\":\"forbidden-admin\",\"password\":\"password123\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
    }

    private String loginToken(String username, String password) throws Exception {
        String response = mockMvc.perform(post("/auth/login")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of(
                        "username", username,
                        "password", password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }
}
