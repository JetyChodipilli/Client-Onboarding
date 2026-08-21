import type { OnboardingStatus,OnboardingStepStatus } from "@/types/onboarding";
import type { ProjectStatus } from "@/types/project";
import type { WorkflowStepType } from "@/types/workflow";
export type OnboardingReviewAction = "REVIEW_STARTED"|"APPROVED"|"REVISION_REQUESTED";
export type FinalReviewRequirement = {stepId:string;stepKey:string;name:string;stepType:WorkflowStepType;displayOrder:number;required:boolean;blocking:boolean;clientVisible:boolean;status:OnboardingStepStatus;satisfied:boolean;revisable:boolean};
export type OnboardingReviewHistory = {id:string;action:OnboardingReviewAction;reviewerUserId:string;reason:string|null;revisionStepIds:string[];onboardingVersion:number;createdAt:string};
export type FinalReviewChecklist = {onboardingId:string;projectId:string;onboardingStatus:OnboardingStatus;projectStatus:ProjectStatus;ready:boolean;blockingTotal:number;blockingCompleted:number;requiredTotal:number;requiredCompleted:number;canApprove:boolean;onboardingVersion:number;projectVersion:number;requirements:FinalReviewRequirement[];history:OnboardingReviewHistory[]};
export type ProjectActivation = {id:string;status:ProjectStatus;version:number};
