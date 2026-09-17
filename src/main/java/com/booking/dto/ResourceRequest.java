package com.booking.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResourceRequest {

    @NotBlank(message = "name is required")
    private String name;

    @NotBlank(message = "type is required")
    private String type;

    private String description;

    private String location;

    @Positive(message = "capacity must be a positive number")
    private Integer capacity;

    @NotNull(message = "pricePerHour is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "pricePerHour must be greater than 0")
    private BigDecimal pricePerHour;

    private Boolean available;
}
