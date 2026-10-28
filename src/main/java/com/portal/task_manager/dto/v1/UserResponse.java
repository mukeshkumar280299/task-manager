package com.portal.task_manager.dto.v1;

import com.portal.task_manager.entity.User;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserResponse {
    private Long id;
    private String firstName;
    private String middleName;
    private String lastName;
    private String email;
    private String role;
    private String displayName;
    private boolean active;
    private String createdDate;

    public static UserResponse from(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .middleName(user.getMiddleName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .displayName(user.getDisplayName())
                .active(user.isActive())
                .createdDate(user.getCreatedDate() != null ? user.getCreatedDate().toString() : null)
                .build();
    }
}
