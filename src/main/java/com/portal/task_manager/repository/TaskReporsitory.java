package com.portal.task_manager.repository;

import com.portal.task_manager.entity.Task;
import com.portal.task_manager.entity.TaskPriority;
import com.portal.task_manager.entity.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface TaskReporsitory extends JpaRepository<Task, Long> {
    // BASIC CRUD OPERATIONS
    //=========================
    Optional<Task> findByIdAndDeletedFalse(Long id);
    Page<Task> findByCreatedByAndDeletedFalse(Long userId, Pageable pageable);
    Page<Task> findByAssignedToAndDeletedFalse(Long userId, Pageable pageable);

    /**
     * Hybrid Search - Best for upto 1 million records
     * - FULLTEXT: Fast natural language search on text
     * - LIKE: Fallback for numbers, short words, special chars
     * - Supports 1 million records efficiently
     *
     */
    @Query(value = "SELECT * FROM tasks t WHERE " +
            "t.deleted = false AND " +
            "t.created_by = :userId AND " +
            "(" +
            "   MATCH(t.title, t.description) AGAINST(:searchTerm IN NATURAL LANGUAGE MODE) " +
            "   OR t.title LIKE CONCAT('%', :searchTerm, '%') " +
            "   OR t.description LIKE CONCAT('%', :searchTerm, '%') " +
            "   OR CAST(t.id AS CHAR) LIKE CONCAT('%', :searchTerm, '%') " +
            ")",
            nativeQuery = true)
    Page<Task> searchTasks(@Param("userId") Long userId,
                           @Param("searchTerm") String searchTerm,
                           Pageable pageable);

    /**
     * Hybrid Search with Relevance Sorting
     * Returns best matching tasks first
     */
    @Query(value = "SELECT t.*, " +
            "MATCH(t.title, t.description) AGAINST(:searchTerm IN NATURAL LANGUAGE MODE) as relevance " +
            "FROM tasks t WHERE " +
            "t.deleted = false AND " +
            "t.created_by = :userId AND " +
            "(MATCH(t.title, t.description) AGAINST(:searchTerm IN NATURAL LANGUAGE MODE) " +
            "OR t.title LIKE CONCAT('%', :searchTerm, '%') " +
            "OR t.description LIKE CONCAT('%', :searchTerm, '%')) " +
            "ORDER BY relevance DESC",
            nativeQuery = true)
    Page<Task> searchTasksByRelevance(@Param("userId") Long userId, @Param("searchTerm") String searchTerm, Pageable pageable);

    Page<Task> findByCreatedByAndStatusAndDeletedFalse(Long userId, TaskStatus status, Pageable pageable);
    Page<Task> findByAssignedToAndStatusAndDeletedFalse(Long userId, TaskStatus status, Pageable pageable);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.status = :status AND t.deleted = false")
    long countTasksByStatusForAdmin(@Param("status") TaskStatus status);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.createdBy = :userId AND t.status = :status AND t.deleted = false")
    long countTasksByStatusAndUser(@Param("userId") Long userId, @Param("status") TaskStatus status);

    Page<Task> findByCreatedByAndPriorityAndDeletedFalse(Long userId, TaskPriority priority, Pageable pageable);
    Page<Task> findByAssignedToAndPriorityAndDeletedFalse(Long userId, TaskPriority priority, Pageable pageable);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.priority = :priority AND t.deleted = false")
    long countTasksByPriorityForAdmin(@Param("priority") TaskPriority priority);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.createdBy = :userId AND t.priority = :priority AND t.deleted = false")
    long countTasksByPriorityAndUser(@Param("userId") Long userId, @Param("priority") TaskPriority priority);

    Page<Task> findByCreatedByAndDueDateBeforeAndDeletedFalse(Long userId, LocalDate date, Pageable pageable);

    Page<Task> findByCreatedByAndDueDateAfterAndDeletedFalse(Long userId, LocalDate date, Pageable pageable);

    Page<Task> findByCreatedByAndDueDateBetweenAndDeletedFalse(Long userId, LocalDate start, LocalDate end, Pageable pageable);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.dueDate < CURRENT_DATE AND t.status != 'DONE' AND t.deleted = false")
    long countOverdueTasksForAdmin();

    @Query("SELECT COUNT(t) FROM Task t WHERE t.createdBy = :userId AND t.dueDate < CURRENT_DATE AND t.status != 'DONE' AND t.deleted = false")
    long countOverdueTasksByUser(@Param("userId") Long userId);

    @Query("SELECT t FROM Task t WHERE t.deleted = false AND t.createdBy = :userId " +
            "AND (:status IS NULL OR t.status = :status) " +
            "AND (:priority IS NULL OR t.priority = :priority) " +
            "AND (:dueDate IS NULL OR t.dueDate <= :dueDate)")
    Page<Task> findTasksWithFilters(@Param("userId") Long userId,
                                    @Param("status") TaskStatus status,
                                    @Param("priority") TaskPriority priority,
                                    @Param("dueDate") LocalDate dueDate,
                                    Pageable pageable);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.createdBy = :userId AND t.deleted = false")
    long countTotalTasksByUser(@Param("userId") Long userId);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.deleted = false")
    long countAllActiveTasks();

    @Query("SELECT COUNT(t) FROM Task t WHERE t.createdAt BETWEEN :start AND :end AND t.deleted = false")
    long countByCreatedAtBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.updatedAt BETWEEN :start AND :end AND t.status = 'DONE' AND t.deleted = false")
    long countCompletedByDate(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    Page<Task> findAllByDeletedFalse(Pageable pageable);

    @Query("SELECT t FROM Task t WHERE t.deleted = false AND t.createdBy = :userId ORDER BY t.updatedAt DESC")
    Page<Task> findRecentTasksByUser(@Param("userId") Long userId, Pageable pageable);

    @Modifying
    @Query("UPDATE Task t SET t.deleted = true WHERE t.id = :taskId AND t.createdBy = :userId")
    int softDeleteTask(@Param("userId") Long userId, @Param("taskId") Long taskId);

    @Modifying
    @Query("UPDATE Task t SET t.assignedTo = :assignedTo WHERE t.id = :taskId AND t.createdBy = :userId")
    int assignTask(@Param("userId") Long userId,
                   @Param("taskId") Long taskId,
                   @Param("assignedTo") Long assignedTo);

    @Modifying
    @Query("UPDATE Task t SET t.status = :status WHERE t.id = :taskId AND t.createdBy = :userId")
    int updateTaskStatus(@Param("userId") Long userId,
                         @Param("taskId") Long taskId,
                         @Param("status") TaskStatus status);
}
