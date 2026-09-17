package com.booking.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "resources")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Resource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    /** e.g. ROOM, VEHICLE, EQUIPMENT — free-text category, not a hardcoded enum, so new resource types don't require a code change */
    @Column(nullable = false, length = 50)
    private String type;

    @Column(length = 1000)
    private String description;

    @Column(length = 150)
    private String location;

    private Integer capacity;

    @Column(name = "price_per_hour", nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePerHour;

    @Builder.Default
    @Column(nullable = false)
    private boolean available = true;
}
