package com.booking.dto;

import com.booking.entity.Role;
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