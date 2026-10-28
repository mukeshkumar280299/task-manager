package com.portal.task_manager.dto.v1;

import com.portal.task_manager.entity.Task;
import com.portal.task_manager.entity.TaskPriority;
import com.portal.task_manager.entity.TaskStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
@Data
@Builder
public class TaskResponse {

    private Long id;
    private String title;
    private String description;
    private TaskStatus status;
    private TaskPriority priority;
    private LocalDate dueDate;
    private Long createdBy;
    private Long assignedTo;
    private Long categoryId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static TaskResponse from(Task task) {
        return TaskResponse.builder()
                .id(task.getId())
                .title(task.getTitle())
                .description(task.getDescription())
                .status(task.getStatus())
                .priority(task.getPriority())
                .dueDate(task.getDueDate())
                .createdBy(task.getCreatedBy())
                .assignedTo(task.getAssignedTo())
                .categoryId(task.getCategoryId())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }
}