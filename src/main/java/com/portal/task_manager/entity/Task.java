package com.portal.task_manager.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "tasks")
public class Task {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "title", nullable = false)
    private String title;
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TaskStatus status;
    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false)
    private TaskPriority priority;
    @Column(name = "due_date")
    private LocalDate dueDate;
    @Column(name = "deleted", nullable = false)
    private boolean deleted;
    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;
    @Column(name = "assigned_to")
    private Long assignedTo;
    @Column(name = "category_id")
    private Long categoryId;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false, updatable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now(Clock.systemUTC());
        updatedAt = LocalDateTime.now(Clock.systemUTC());
        if (status == null) {
            status = TaskStatus.TODO;
        }
        if (priority == null) {
            priority = TaskPriority.MEDIUM;
        }
        deleted = false;
    }

    @PreUpdate
    protected void update() {
        updatedAt = LocalDateTime.now(Clock.systemUTC());
    }
}
