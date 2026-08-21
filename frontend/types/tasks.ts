export type TaskType = "MANUAL" | "SYSTEM_GENERATED" | "WORKFLOW_GENERATED";
export type TaskStatus = "TODO" | "IN_PROGRESS" | "BLOCKED" | "IN_REVIEW" | "COMPLETED" | "CANCELLED";
export type TaskPriority = "LOW" | "MEDIUM" | "HIGH" | "URGENT";
export type TaskItem = {
  id:string; projectId:string|null; onboardingId:string|null; stepId:string|null; title:string; description:string|null;
  taskType:TaskType; status:TaskStatus; priority:TaskPriority; assignedUserId:string|null; assignedUserType:"INTERNAL"|"CLIENT"|null;
  assignedRoleId:string|null; dueAt:string|null; completedAt:string|null; createdAt:string; updatedAt:string; version:number;
};
