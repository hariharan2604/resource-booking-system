package com.booking.service;

import com.booking.dto.ReservationCreateRequest;
import com.booking.dto.ReservationResponse;
import com.booking.dto.ReservationUpdateRequest;
import com.booking.entity.Reservation;
import com.booking.entity.ReservationStatus;
import com.booking.entity.Resource;
import com.booking.entity.Role;
import com.booking.entity.User;
import com.booking.exception.ForbiddenOperationException;
import com.booking.exception.InvalidReservationException;
import com.booking.exception.ResourceNotFoundException;
import com.booking.repository.ReservationRepository;
import com.booking.repository.UserRepository;
import com.booking.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;

@Service
@RequiredArgsConstructor
@Transactional
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final ResourceService resourceService;

    /**
     * Lists reservations honoring RBAC scope: ADMIN sees everything matching the
     * filters,
     * USER is always additionally scoped to their own reservations regardless of
     * any
     * caller-supplied parameter, since no userId is ever accepted from the client
     * here.
     */
    @Transactional(readOnly = true)
    public Page<ReservationResponse> list(
            UserPrincipal principal,
            ReservationStatus status,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Pageable pageable) {

        Long scopeUserId = isAdmin(principal) ? null : principal.getId();

        return reservationRepository
                .findReservations(
                        scopeUserId,
                        status,
                        minPrice,
                        maxPrice,
                        pageable)
                .map(this::toResponse);
    }

    @Cacheable(value = "reservations", key = "#principal.id + ':' + #id")
    @Transactional(readOnly = true)
    public ReservationResponse getById(UserPrincipal principal, Long id) {
        Reservation reservation = findEntity(id);
        assertCanView(principal, reservation);
        return toResponse(reservation);
    }

    @CachePut(value = "reservations", key = "#result.id")
    public ReservationResponse create(UserPrincipal principal, ReservationCreateRequest request) {
        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new InvalidReservationException("endTime must be after startTime");
        }

        Resource resource = resourceService.findEntityForUpdate(request.getResourceId());
        if (!resource.isAvailable()) {
            throw new InvalidReservationException("Resource is not currently available for booking");
        }
        assertNoOverlap(resource, request.getStartTime(), request.getEndTime(), null);

        User bookingUser;
        if (isAdmin(principal) && request.getUserId() != null) {
            bookingUser = userRepository.findById(request.getUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + request.getUserId()));
        } else {
            bookingUser = userRepository.findById(principal.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));
        }

        BigDecimal price = calculatePrice(resource.getPricePerHour(), request.getStartTime(), request.getEndTime());

        Reservation reservation = Reservation.builder()
                .resource(resource)
                .user(bookingUser)
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .status(ReservationStatus.PENDING)
                .price(price)
                .build();

        Reservation savedReservation = reservationRepository.save(reservation);
        return toResponse(savedReservation);
    }

    /** Full update — ADMIN only; enforced via @PreAuthorize at the controller. */
    @CachePut(value = "reservations", key = "#id")
    public ReservationResponse update(Long id, ReservationUpdateRequest request) {
        Reservation reservation = findEntity(id);

        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new InvalidReservationException("endTime must be after startTime");
        }

        Resource resource = resourceService.findEntityForUpdate(request.getResourceId());
        if (request.getStatus() != ReservationStatus.CANCELLED && !resource.isAvailable()) {
            throw new InvalidReservationException("Resource is not currently available for booking");
        }
        if (request.getStatus() != ReservationStatus.CANCELLED) {
            assertNoOverlap(resource, request.getStartTime(), request.getEndTime(), id);
        }

        reservation.setResource(resource);
        reservation.setStartTime(request.getStartTime());
        reservation.setEndTime(request.getEndTime());
        reservation.setStatus(request.getStatus());
        reservation.setPrice(calculatePrice(resource.getPricePerHour(), request.getStartTime(), request.getEndTime()));

        return toResponse(reservationRepository.save(reservation));
    }

    /** USER may cancel only their own reservation; ADMIN may cancel any. */
    @CachePut(value = "reservations", key = "#id")
    public ReservationResponse cancel(UserPrincipal principal, Long id) {
        Reservation reservation = findEntity(id);
        assertCanView(principal, reservation);

        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            throw new InvalidReservationException("Reservation is already cancelled");
        }

        reservation.setStatus(ReservationStatus.CANCELLED);
        Reservation savedReservation = reservationRepository.save(reservation);
        return toResponse(savedReservation);
    }

    /** ADMIN only; enforced via @PreAuthorize at the controller. */
    @CacheEvict(value = "reservations", key = "#id")
    public void delete(Long id) {
        Reservation reservation = findEntity(id);
        if (reservation.getStatus() != ReservationStatus.CANCELLED) {
            throw new InvalidReservationException("Only cancelled reservations can be deleted");
        }

        reservationRepository.deleteById(id);
    }

    private Reservation findEntity(Long id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found with id: " + id));
    }

    private void assertCanView(UserPrincipal principal, Reservation reservation) {
        if (!isAdmin(principal) && !reservation.getUser().getId().equals(principal.getId())) {
            throw new ForbiddenOperationException("You may only access your own reservations");
        }
    }

    private boolean isAdmin(UserPrincipal principal) {
        return principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + Role.ADMIN.name()));
    }

    private void assertNoOverlap(Resource resource, java.time.LocalDateTime startTime,
            java.time.LocalDateTime endTime, Long reservationId) {
        if (reservationRepository.existsOverlappingReservation(
                resource.getId(), startTime, endTime, ReservationStatus.CANCELLED, reservationId)) {
            throw new InvalidReservationException("Resource is already reserved during the requested time");
        }
    }

    private BigDecimal calculatePrice(BigDecimal pricePerHour, java.time.LocalDateTime start,
            java.time.LocalDateTime end) {
        double hours = Duration.between(start, end).toMinutes() / 60.0;
        return pricePerHour.multiply(BigDecimal.valueOf(hours)).setScale(2, RoundingMode.HALF_UP);
    }

    private ReservationResponse toResponse(Reservation r) {
        return ReservationResponse.builder()
                .id(r.getId())
                .resourceId(r.getResource().getId())
                .resourceName(r.getResource().getName())
                .userId(r.getUser().getId())
                .username(r.getUser().getUsername())
                .startTime(r.getStartTime())
                .endTime(r.getEndTime())
                .status(r.getStatus())
                .price(r.getPrice())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
