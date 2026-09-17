package com.booking.repository;

import com.booking.entity.Reservation;
import com.booking.entity.ReservationStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
        long countByStatus(ReservationStatus status);

        @Query("""
                        select r
                        from Reservation r
                        where (:userId is null or r.user.id = :userId)
                        and (:status is null or r.status = :status)
                        and (:minPrice is null or r.price >= :minPrice)
                        and (:maxPrice is null or r.price <= :maxPrice)
                        """)
        Page<Reservation> findReservations(
                        @Param("userId") Long userId,
                        @Param("status") ReservationStatus status,
                        @Param("minPrice") BigDecimal minPrice,
                        @Param("maxPrice") BigDecimal maxPrice,
                        Pageable pageable);

        @Query("""
                        select count(r) > 0 from Reservation r
                        where r.resource.id = :resourceId
                        and r.status <> :cancelledStatus
                        and (:reservationId is null or r.id <> :reservationId)
                        and r.startTime < :endTime
                        and r.endTime > :startTime
                        """)
        boolean existsOverlappingReservation(@Param("resourceId") Long resourceId,
                        @Param("startTime") LocalDateTime startTime,
                        @Param("endTime") LocalDateTime endTime,
                        @Param("cancelledStatus") ReservationStatus cancelledStatus,
                        @Param("reservationId") Long reservationId);
}
