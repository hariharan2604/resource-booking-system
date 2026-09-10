package com.example.booking.dto;

import com.example.booking.entity.Role;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AdminUserCreateRequest extends RegisterRequest {

    @NotNull(message = "role is required")
    private Role role;
}