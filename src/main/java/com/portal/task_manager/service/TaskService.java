package com.portal.task_manager.service;

import com.portal.task_manager.audit.service.AuditService;
import com.portal.task_manager.dto.v1.TaskRequest;
import com.portal.task_manager.dto.v1.TaskResponse;
import com.portal.task_manager.entity.Task;
import com.portal.task_manager.entity.TaskStatus;
import com.portal.task_manager.repository.TaskReporsitory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskReporsitory taskRepository;
    private final AuditService auditService;

    @Transactional
    public TaskResponse createTask(TaskRequest request, Long currentUserId) {
        log.info("Creating task for user: {}", currentUserId);

        Task task = Task.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .priority(request.getPriority())
                .status(request.getStatus() != null ? request.getStatus() : TaskStatus.TODO)
                .dueDate(request.getDueDate())
                .createdBy(currentUserId)
                .assignedTo(request.getAssignedTo())
                .categoryId(request.getCategoryId())
                .deleted(false)
                .build();
        Task savedTask = taskRepository.save(task);

        auditService.logChange("Task", savedTask.getId(), null, null, task.getTitle() ,"CREATE");

        return TaskResponse.from(savedTask);
    }

    @Transactional(readOnly = true)
    public TaskResponse getTaskById(Long taskId, Long currentUserId, boolean isAdmin) {
        Task task = taskRepository.findByIdAndDeletedFalse(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        // Check permission
        boolean isCreator = task.getCreatedBy().equals(currentUserId);
        boolean isAssignee = task.getAssignedTo() != null && task.getAssignedTo().equals(currentUserId);

        if (!isCreator && !isAssignee && !isAdmin) {
            throw new RuntimeException("You don't have permission to view this task");
        }

        return TaskResponse.from(task);
    }

    @Transactional(readOnly = true)
    public Page<TaskResponse> getMyTasks(Long currentUserId, Pageable pageable) {
        return taskRepository.findByCreatedByAndDeletedFalse(currentUserId, pageable)
                .map(TaskResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<TaskResponse> getTasksAssignedToMe(Long currentUserId, Pageable pageable) {
        return taskRepository.findByAssignedToAndDeletedFalse(currentUserId, pageable)
                .map(TaskResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<TaskResponse> searchTasks(Long currentUserId, String keyword, Pageable pageable) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return getMyTasks(currentUserId, pageable);
        }
        return taskRepository.searchTasks(currentUserId, keyword.trim(), pageable)
                .map(TaskResponse::from);
    }

    @Transactional
    public TaskResponse updateTask(Long taskId, TaskRequest request, Long currentUserId, boolean isAdmin) {
        Task task = taskRepository.findByIdAndDeletedFalse(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        // Check permission
        boolean isCreator = task.getCreatedBy().equals(currentUserId);

        if (!isCreator && !isAdmin) {
            throw new RuntimeException("Only task creator or admin can update tasks");
        }

        // Track changes for audit
        String oldTitle = task.getTitle();
        String oldStatus = task.getStatus().name();
        String oldPriority = task.getPriority().name();

        // Update fields
        if (request.getTitle() != null) task.setTitle(request.getTitle());
        if (request.getDescription() != null) task.setDescription(request.getDescription());
        if (request.getPriority() != null) task.setPriority(request.getPriority());
        if (request.getStatus() != null) task.setStatus(request.getStatus());
        if (request.getDueDate() != null) task.setDueDate(request.getDueDate());
        if (request.getCategoryId() != null) task.setCategoryId(request.getCategoryId());

        Task updatedTask = taskRepository.save(task);

        // Audit changes
        if (!oldTitle.equals(task.getTitle())) {
            auditService.logChange("Task", taskId, "title", oldTitle, task.getTitle(), "UPDATE");
        }
        if (!oldStatus.equals(task.getStatus().name())) {
            auditService.logChange("Task", taskId, "status", oldStatus, task.getStatus().name(), "UPDATE");
        }
        if (!oldPriority.equals(task.getPriority().name())) {
            auditService.logChange("Task", taskId, "priority", oldPriority, task.getPriority().name(), "UPDATE");
        }

        return TaskResponse.from(updatedTask);
    }
    @Transactional
    public TaskResponse updateTaskStatus(Long taskId, TaskStatus newStatus, Long currentUserId, boolean isAdmin) {
        Task task = taskRepository.findByIdAndDeletedFalse(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        // Check permission
        boolean isCreator = task.getCreatedBy().equals(currentUserId);
        boolean isAssignee = task.getAssignedTo() != null && task.getAssignedTo().equals(currentUserId);

        if (!isCreator && !isAssignee && !isAdmin) {
            throw new RuntimeException("Only creator, assignee, or admin can update task status");
        }

        String oldStatus = task.getStatus().name();
        task.setStatus(newStatus);

        Task updatedTask = taskRepository.save(task);

        auditService.logChange("Task", taskId, "status", oldStatus, newStatus.name(), "UPDATE");

        return TaskResponse.from(updatedTask);
    }

    @Transactional
    public TaskResponse assignTask(Long taskId, Long assignToUserId, Long currentUserId, boolean isAdmin) {
        Task task = taskRepository.findByIdAndDeletedFalse(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        // Check permission
        boolean isCreator = task.getCreatedBy().equals(currentUserId);

        if (!isCreator && !isAdmin) {
            throw new RuntimeException("Only task creator or admin can assign tasks");
        }

        String oldAssignee = task.getAssignedTo() != null ? String.valueOf(task.getAssignedTo()) : "null";
        task.setAssignedTo(assignToUserId);

        Task updatedTask = taskRepository.save(task);

        auditService.logChange("Task", taskId, "assigned_to", oldAssignee, String.valueOf(assignToUserId), "UPDATE");

        return TaskResponse.from(updatedTask);
    }

    @Transactional
    public void deleteTask(Long taskId, Long currentUserId, boolean isAdmin) {
        Task task = taskRepository.findByIdAndDeletedFalse(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        // Check permission
        boolean isCreator = task.getCreatedBy().equals(currentUserId);

        if (!isCreator && !isAdmin) {
            throw new RuntimeException("Only task creator or admin can delete tasks");
        }

        // Soft delete
        task.setDeleted(true);
        taskRepository.save(task);

        auditService.logChange("Task", taskId, "deleted", task.getTitle(), null, "DELETE");

        log.info("Task {} deleted by user {} (admin: {})", taskId, currentUserId, isAdmin);
    }


}
