package com.portal.task_manager.controller.v1;

import com.portal.task_manager.dto.v1.TaskRequest;
import com.portal.task_manager.dto.v1.TaskResponse;
import com.portal.task_manager.entity.TaskStatus;
import com.portal.task_manager.security.UserPrincipal;
import com.portal.task_manager.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;


    @PostMapping
    public ResponseEntity<TaskResponse> createTask(
            @Valid @RequestBody TaskRequest request,
            Authentication authentication) {

        UserPrincipal currentUser = (UserPrincipal) authentication.getPrincipal();
        TaskResponse response = taskService.createTask(request, currentUser.getId());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskResponse> getTaskById(
            @PathVariable Long id,
            Authentication authentication) {

        UserPrincipal currentUser = (UserPrincipal) authentication.getPrincipal();
        boolean isAdmin = isAdmin(currentUser);

        TaskResponse response = taskService.getTaskById(id, currentUser.getId(), isAdmin);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/my-tasks")
    public ResponseEntity<Page<TaskResponse>> getMyTasks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            Authentication authentication) {

        UserPrincipal currentUser = (UserPrincipal) authentication.getPrincipal();

        Sort.Direction direction = sortDir.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

        Page<TaskResponse> tasks = taskService.getMyTasks(currentUser.getId(), pageable);

        return ResponseEntity.ok(tasks);
    }

    @GetMapping("/assigned-to-me")
    public ResponseEntity<Page<TaskResponse>> getTasksAssignedToMe(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "dueDate") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            Authentication authentication) {

        UserPrincipal currentUser = (UserPrincipal) authentication.getPrincipal();

        Sort.Direction direction = sortDir.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

        Page<TaskResponse> tasks = taskService.getTasksAssignedToMe(currentUser.getId(), pageable);

        return ResponseEntity.ok(tasks);
    }

    @GetMapping("/search")
    public ResponseEntity<Page<TaskResponse>> searchTasks(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {

        UserPrincipal currentUser = (UserPrincipal) authentication.getPrincipal();
        Pageable pageable = PageRequest.of(page, size);

        Page<TaskResponse> tasks = taskService.searchTasks(currentUser.getId(), q, pageable);

        return ResponseEntity.ok(tasks);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TaskResponse> updateTask(
            @PathVariable Long id,
            @Valid @RequestBody TaskRequest request,
            Authentication authentication) {

        UserPrincipal currentUser = (UserPrincipal) authentication.getPrincipal();
        boolean isAdmin = isAdmin(currentUser);

        TaskResponse response = taskService.updateTask(id, request, currentUser.getId(), isAdmin);

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<TaskResponse> updateTaskStatus(
            @PathVariable Long id,
            @RequestParam String status,
            Authentication authentication) {

        UserPrincipal currentUser = (UserPrincipal) authentication.getPrincipal();
        boolean isAdmin = isAdmin(currentUser);

        TaskStatus taskStatus = TaskStatus.valueOf(status.toUpperCase());
        TaskResponse response = taskService.updateTaskStatus(id, taskStatus, currentUser.getId(), isAdmin);

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/assign")
    public ResponseEntity<TaskResponse> assignTask(
            @PathVariable Long id,
            @RequestParam Long userId,
            Authentication authentication) {

        UserPrincipal currentUser = (UserPrincipal) authentication.getPrincipal();
        boolean isAdmin = isAdmin(currentUser);

        TaskResponse response = taskService.assignTask(id, userId, currentUser.getId(), isAdmin);

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteTask(
            @PathVariable Long id,
            Authentication authentication) {

        UserPrincipal currentUser = (UserPrincipal) authentication.getPrincipal();
        boolean isAdmin = isAdmin(currentUser);

        taskService.deleteTask(id, currentUser.getId(), isAdmin);

        return ResponseEntity.ok(Map.of(
                "message", "Task deleted successfully",
                "taskId", id
        ));
    }

    private boolean isAdmin(UserPrincipal user) {
        return user.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }
}