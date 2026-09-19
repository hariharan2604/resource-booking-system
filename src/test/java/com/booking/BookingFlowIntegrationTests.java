package com.booking;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureWebMvc;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BookingFlowIntegrationTests {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @Autowired
        private CacheManager cacheManager;

        @Test
        void resourceListReflectsNewResourceAfterCreate() throws Exception {
                String userToken = login("user", "user123");
                String adminToken = login("admin", "admin123");

                mockMvc.perform(get("/api/resources")
                                .header("Authorization", bearer(userToken)))
                                .andExpect(status().isOk());

                String resourceName = "Cache Room " + System.nanoTime();
                String resourceJson = objectMapper.writeValueAsString(Map.of(
                                "name", resourceName,
                                "type", "ROOM",
                                "description", "Cache invalidation test",
                                "location", "Test floor",
                                "capacity", 4,
                                "pricePerHour", 25.00,
                                "available", true));

                mockMvc.perform(post("/api/resources")
                                .header("Authorization", bearer(adminToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(resourceJson))
                                .andExpect(status().isCreated());

                mockMvc.perform(get("/api/resources")
                                .header("Authorization", bearer(userToken)))
                                .andExpect(status().isOk())
                                .andExpect(content().string(containsString(resourceName)));
        }

        @Test
        void reservationCacheIsInvalidatedAfterAdminUpdate() throws Exception {
                cacheManager.getCache("reservations").clear();
                String userToken = login("user", "user123");
                String adminToken = login("admin", "admin123");
                long resourceId = firstResourceId(userToken);

                MvcResult created = mockMvc.perform(post("/api/reservations")
                                .header("Authorization", bearer(userToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(reservationPayload(resourceId, "2050-01-10T09:00:00", "2050-01-10T10:00:00")))
                                .andExpect(status().isCreated())
                                .andReturn();
                long reservationId = json(created).get("id").asLong();

                mockMvc.perform(get("/api/reservations/{id}", reservationId)
                                .header("Authorization", bearer(userToken)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("PENDING"));

                mockMvc.perform(put("/api/reservations/{id}", reservationId)
                                .header("Authorization", bearer(adminToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"resourceId\":" + resourceId
                                                + ",\"startTime\":\"2050-01-10T10:00:00\","
                                                + "\"endTime\":\"2050-01-10T11:00:00\","
                                                + "\"status\":\"CONFIRMED\"}"))
                                .andExpect(status().isOk());

                mockMvc.perform(get("/api/reservations/{id}", reservationId)
                                .header("Authorization", bearer(userToken)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        }

        @Test
        void adminCanCreateUpdateAndDeleteResource() throws Exception {
                String adminToken = login("admin", "admin123");
                String resourceName = "Integration Room " + System.nanoTime();
                String resourceJson = objectMapper.writeValueAsString(Map.of(
                                "name", resourceName,
                                "type", "ROOM",
                                "description", "Integration test room",
                                "location", "Test floor",
                                "capacity", 12,
                                "pricePerHour", 40.00,
                                "available", true));

                MvcResult created = mockMvc.perform(post("/api/resources")
                                .header("Authorization", bearer(adminToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(resourceJson))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.id", notNullValue()))
                                .andExpect(jsonPath("$.name").value(resourceName))
                                .andReturn();
                long resourceId = json(created).get("id").asLong();

                mockMvc.perform(put("/api/resources/{id}", resourceId)
                                .header("Authorization", bearer(adminToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(resourceJson.replace(resourceName, resourceName + " Updated")))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.name").value(resourceName + " Updated"));

                mockMvc.perform(get("/api/resources/{id}", resourceId)
                                .header("Authorization", bearer(adminToken)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.name").value(resourceName + " Updated"));

                mockMvc.perform(delete("/api/resources/{id}", resourceId)
                                .header("Authorization", bearer(adminToken)))
                                .andExpect(status().isNoContent());

                mockMvc.perform(get("/api/resources/{id}", resourceId)
                                .header("Authorization", bearer(adminToken)))
                                .andExpect(status().isNotFound());
        }

        @Test
        void userCanReadResourcesButCannotWriteThem() throws Exception {
                String userToken = login("user", "user123");

                mockMvc.perform(get("/api/resources")
                                .header("Authorization", bearer(userToken)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content").isArray());

                mockMvc.perform(post("/api/resources")
                                .header("Authorization", bearer(userToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Forbidden\",\"type\":\"ROOM\",\"pricePerHour\":10}"))
                                .andExpect(status().isForbidden());
        }

        @Test
        void userCanCreateFilterAndCancelOwnReservation() throws Exception {
                String userToken = login("user", "user123");
                long resourceId = firstResourceId(userToken);
                String start = "2030-01-10T09:00:00";
                String end = "2030-01-10T11:00:00";

                MvcResult created = mockMvc.perform(post("/api/reservations")
                                .header("Authorization", bearer(userToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(reservationPayload(resourceId, start, end)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.status").value("PENDING"))
                                .andExpect(jsonPath("$.price").value(30.00))
                                .andReturn();
                long reservationId = json(created).get("id").asLong();

                mockMvc.perform(get("/api/reservations")
                                .header("Authorization", bearer(userToken))
                                .param("status", "PENDING")
                                .param("minPrice", "20")
                                .param("maxPrice", "40"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content").isArray())
                                .andExpect(content().string(containsString("PENDING")));

                mockMvc.perform(patch("/api/reservations/{id}/cancel", reservationId)
                                .header("Authorization", bearer(userToken)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("CANCELLED"));

                mockMvc.perform(patch("/api/reservations/{id}/cancel", reservationId)
                                .header("Authorization", bearer(userToken)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void userCannotAccessAnotherUsersReservation() throws Exception {
                String userToken = login("user", "user123");
                String aliceToken = login("alice", "alice123");
                long resourceId = firstResourceId(userToken);

                MvcResult created = mockMvc.perform(post("/api/reservations")
                                .header("Authorization", bearer(userToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(reservationPayload(resourceId, "2031-02-10T09:00:00", "2031-02-10T10:00:00")))
                                .andExpect(status().isCreated())
                                .andReturn();
                long reservationId = json(created).get("id").asLong();

                mockMvc.perform(get("/api/reservations/{id}", reservationId)
                                .header("Authorization", bearer(aliceToken)))
                                .andExpect(status().isForbidden());

                mockMvc.perform(patch("/api/reservations/{id}/cancel", reservationId)
                                .header("Authorization", bearer(aliceToken)))
                                .andExpect(status().isForbidden());
        }

        @Test
        void adminCanUpdateCancelAndDeleteReservation() throws Exception {
                String userToken = login("user", "user123");
                String adminToken = login("admin", "admin123");
                long resourceId = firstResourceId(userToken);

                MvcResult created = mockMvc.perform(post("/api/reservations")
                                .header("Authorization", bearer(userToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(reservationPayload(resourceId, "2032-03-10T09:00:00", "2032-03-10T10:00:00")))
                                .andExpect(status().isCreated())
                                .andReturn();
                long reservationId = json(created).get("id").asLong();

                mockMvc.perform(put("/api/reservations/{id}", reservationId)
                                .header("Authorization", bearer(adminToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"resourceId\":" + resourceId
                                                + ",\"startTime\":\"2032-03-10T10:00:00\","
                                                + "\"endTime\":\"2032-03-10T12:00:00\","
                                                + "\"status\":\"CONFIRMED\"}"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                                .andExpect(jsonPath("$.price").value(30.00));

                mockMvc.perform(patch("/api/reservations/{id}/cancel", reservationId)
                                .header("Authorization", bearer(adminToken)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("CANCELLED"));

                mockMvc.perform(delete("/api/reservations/{id}", reservationId)
                                .header("Authorization", bearer(adminToken)))
                                .andExpect(status().isNoContent());
        }

        @Test
        void invalidReservationAndResourceRequestsReturnValidationErrors() throws Exception {
                String adminToken = login("admin", "admin123");

                mockMvc.perform(post("/api/resources")
                                .header("Authorization", bearer(adminToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"\",\"type\":\"\",\"pricePerHour\":0}"))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").value("Validation Failed"))
                                .andExpect(jsonPath("$.fieldErrors").exists());

                mockMvc.perform(post("/api/reservations")
                                .header("Authorization", bearer(adminToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"resourceId\":999999,\"startTime\":\"2020-01-01T09:00:00\","
                                                + "\"endTime\":\"2020-01-01T10:00:00\"}"))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").value("Validation Failed"));
        }

        @Test
        void overlappingReservationsAreRejected() throws Exception {
                String userToken = login("user", "user123");
                long resourceId = firstResourceId(userToken);

                mockMvc.perform(post("/api/reservations")
                                .header("Authorization", bearer(userToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(reservationPayload(resourceId, "2040-06-10T09:00:00", "2040-06-10T11:00:00")))
                                .andExpect(status().isCreated());

                mockMvc.perform(post("/api/reservations")
                                .header("Authorization", bearer(userToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(reservationPayload(resourceId, "2040-06-10T10:00:00", "2040-06-10T12:00:00")))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message")
                                                .value("Resource is already reserved during the requested time"));
        }

        @Test
        void malformedJsonReturnsBadRequest() throws Exception {
                String adminToken = login("admin", "admin123");

                mockMvc.perform(post("/api/resources")
                                .header("Authorization", bearer(adminToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":"))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").value("Bad Request"));
        }

        @Test
        void concurrentBookingsForSameResourceAllowOnlyOneReservation() throws Exception {
                String userToken = login("user", "user123");
                String aliceToken = login("alice", "alice123");
                long resourceId = firstResourceId(userToken);
                String payload = reservationPayload(resourceId, "2042-07-10T09:00:00", "2042-07-10T11:00:00");
                CountDownLatch start = new CountDownLatch(1);
                ExecutorService executor = Executors.newFixedThreadPool(2);

                Future<Integer> first = submitBooking(executor, start, userToken, payload);
                Future<Integer> second = submitBooking(executor, start, aliceToken, payload);
                start.countDown();

                int firstStatus = first.get();
                int secondStatus = second.get();
                executor.shutdownNow();

                org.hamcrest.MatcherAssert.assertThat(
                                java.util.List.of(firstStatus, secondStatus),
                                org.hamcrest.Matchers.containsInAnyOrder(201, 400));
        }

        private Future<Integer> submitBooking(ExecutorService executor, CountDownLatch start,
                        String token, String payload) {
                return executor.submit(() -> {
                        start.await();
                        return mockMvc.perform(post("/api/reservations")
                                        .header("Authorization", bearer(token))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(payload))
                                        .andReturn().getResponse().getStatus();
                });
        }

        private String login(String username, String password) throws Exception {
                MvcResult result = mockMvc.perform(post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                                "username", username,
                                                "password", password))))
                                .andExpect(status().isOk())
                                .andReturn();
                return json(result).get("token").asString();
        }

        private long firstResourceId(String token) throws Exception {
                MvcResult result = mockMvc.perform(get("/api/resources")
                                .header("Authorization", bearer(token)))
                                .andExpect(status().isOk())
                                .andReturn();
                return json(result).get("content").get(0).get("id").asLong();
        }

        private String reservationPayload(long resourceId, String start, String end) {
                return "{\"resourceId\":" + resourceId
                                + ",\"startTime\":\"" + start + "\",\"endTime\":\"" + end + "\"}";
        }

        private JsonNode json(MvcResult result) throws Exception {
                return objectMapper.readTree(result.getResponse().getContentAsString());
        }

        private String bearer(String token) {
                return "Bearer " + token;
        }
}
