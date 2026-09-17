package com.booking.controller;

import com.booking.dto.ReservationCreateRequest;
import com.booking.dto.ReservationResponse;
import com.booking.dto.ReservationUpdateRequest;
import com.booking.entity.ReservationStatus;
import com.booking.security.UserPrincipal;
import com.booking.service.ReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@Slf4j 
@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
@Tag(name = "Reservations")
public class ReservationController {

    private final ReservationService reservationService;

    @GetMapping
    @Operation(summary = "List reservations — ADMIN sees all, USER sees only their own",
            description = "Supports filtering by status/minPrice/maxPrice, pagination via page/size, " +
                    "and sorting via ?sort=field,dir (e.g. sort=startTime,desc)")
    public ResponseEntity<Page<ReservationResponse>> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Filter by reservation status") @RequestParam(required = false) ReservationStatus status,
            @Parameter(description = "Minimum price (inclusive)") @RequestParam(required = false) BigDecimal minPrice,
            @Parameter(description = "Maximum price (inclusive)") @RequestParam(required = false) BigDecimal maxPrice,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {

        return ResponseEntity.ok(reservationService.list(principal, status, minPrice, maxPrice, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a reservation by id — ADMIN any, USER only their own")
    public ResponseEntity<ReservationResponse> getById(@AuthenticationPrincipal UserPrincipal principal,
                                                        @PathVariable Long id) {
        return ResponseEntity.ok(reservationService.getById(principal, id));
    }

    @PostMapping
    @Operation(summary = "Create a reservation",
            description = "The reservation owner is always resolved from the JWT for USER callers; " +
                    "an ADMIN may optionally supply userId in the body to book on someone else's behalf.")
    public ResponseEntity<ReservationResponse> create(@AuthenticationPrincipal UserPrincipal principal,
                                                       @Valid @RequestBody ReservationCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reservationService.create(principal, request));
    }

    @PatchMapping("/{id}/cancel")
    @Operation(summary = "Cancel a reservation — ADMIN any, USER only their own")
    public ResponseEntity<ReservationResponse> cancel(@AuthenticationPrincipal UserPrincipal principal,
                                                       @PathVariable Long id) {
        return ResponseEntity.ok(reservationService.cancel(principal, id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Full update of a reservation (resource, times, status) — ADMIN only")
    public ResponseEntity<ReservationResponse> update(@PathVariable Long id,
                                                       @Valid @RequestBody ReservationUpdateRequest request) {
        return ResponseEntity.ok(reservationService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a reservation — ADMIN only")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        reservationService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
