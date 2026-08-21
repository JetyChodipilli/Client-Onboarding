package com.brainserve.onboarding.tasks.application.service;

import com.brainserve.onboarding.common.error.ApiException;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Application-layer validation for task assignees. Database triggers repeat these checks defensively. */
@Component
public class TaskAssignmentPolicy {
    private final JdbcTemplate jdbc;

    public TaskAssignmentPolicy(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void validate(UUID organizationId, UUID projectId, UUID userId, String userType, UUID roleId) {
        if (userId != null && roleId != null) {
            throw bad("Assign a task to either a user or a role, not both.");
        }
        if (userId == null && userType != null) throw bad("Assignee type requires an assignee user.");
        if (userId != null && (userType == null || userType.isBlank())) throw bad("Assignee type is required.");
        if (roleId != null) {
            Boolean active = jdbc.queryForObject(
                    "SELECT EXISTS(SELECT 1 FROM client_onboarding.roles WHERE organization_id=? AND id=? AND status='ACTIVE')",
                    Boolean.class, organizationId, roleId);
            if (!Boolean.TRUE.equals(active)) throw bad("Assigned role is not active in this organization.");
        }
        if (userId == null) return;
        String type = userType.trim().toUpperCase(Locale.ROOT);
        if ("INTERNAL".equals(type)) {
            Boolean active = jdbc.queryForObject(
                    "SELECT EXISTS(SELECT 1 FROM client_onboarding.organization_users WHERE organization_id=? AND user_id=? AND status='ACTIVE')",
                    Boolean.class, organizationId, userId);
            if (!Boolean.TRUE.equals(active)) throw bad("Assigned internal user is not active in this organization.");
            return;
        }
        if ("CLIENT".equals(type)) {
            Boolean active = projectId == null
                    ? jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM client_onboarding.client_users WHERE organization_id=? AND user_id=? AND status='ACTIVE')", Boolean.class, organizationId, userId)
                    : jdbc.queryForObject("""
                        SELECT EXISTS(
                          SELECT 1
                          FROM client_onboarding.client_users cu
                          JOIN client_onboarding.client_user_projects cup
                            ON cup.organization_id=cu.organization_id AND cup.client_user_id=cu.id
                          WHERE cu.organization_id=? AND cu.user_id=? AND cu.status='ACTIVE'
                            AND cup.project_id=? AND cup.status='ACTIVE'
                        )
                        """, Boolean.class, organizationId, userId, projectId);
            if (!Boolean.TRUE.equals(active)) throw bad("Assigned client user does not have active access to this task scope.");
            return;
        }
        throw bad("Assignee type must be INTERNAL or CLIENT.");
    }

    private static ApiException bad(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "TASK_ASSIGNEE_INVALID", message);
    }
}
