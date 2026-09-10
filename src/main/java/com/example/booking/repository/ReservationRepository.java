package com.example.booking.repository;

import com.example.booking.entity.Reservation;
import com.example.booking.entity.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface ReservationRepository extends JpaRepository<Reservation, Long>,
                JpaSpecificationExecutor<Reservation> {
        long countByStatus(ReservationStatus status);

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
