package com.portal.task_manager.dto.v1;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginResponse {
    private String token;
    private String type;
    private String role;
    private Long userId;
    private String email;
    private String displayName;
    private boolean mfaEnabled;
}
