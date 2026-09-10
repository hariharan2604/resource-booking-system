package com.example.booking.dto;

import com.example.booking.entity.Role;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class UserResponse {
    Long id;
    String username;
    Role role;
    boolean enabled;
}