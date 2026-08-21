export type ContractStatus = "DRAFT"|"GENERATED"|"SENT"|"VIEWED"|"SIGNED"|"DECLINED"|"EXPIRED"|"VOID"|"CANCELLED";
export type ContractTemplateStatus = "ACTIVE"|"ARCHIVED";
export type ContractTemplateVersionStatus = "DRAFT"|"PUBLISHED";
export type ContractRecipientStatus = "PENDING"|"VIEWED"|"SIGNED"|"DECLINED";
export interface ContractTemplateVersion { id:string;versionNumber:number;status:ContractTemplateVersionStatus;title:string;legalContent:string;contentHash:string;publishedAt:string|null;createdAt:string;updatedAt:string;version:number }
export interface ContractTemplate { id:string;name:string;description:string|null;status:ContractTemplateStatus;createdAt:string;updatedAt:string;version:number;versions:ContractTemplateVersion[] }
export interface ContractRecipient { id:string;contactId:string;displayName:string;email:string;signingOrder:number;status:ContractRecipientStatus;viewedAt:string|null;signedAt:string|null;declinedAt:string|null }
export interface ContractSignature { id:string;recipientId:string;signatoryName:string;signatoryEmail:string;signedAt:string }
export interface ContractVersion { id:string;versionNumber:number;templateVersionId:string;title:string;legalContent:string;contentHash:string;sentAt:string|null;signedDocumentAvailable:boolean;signedDocumentSha256:string|null;signedDocumentSize:number|null;signedDocumentStoredAt:string|null }
export interface ContractSummary { id:string;projectId:string;subject:string;status:ContractStatus;expiresAt:string|null;sentAt:string|null;signedAt:string|null;createdAt:string;version:number }
export interface ContractDetail { id:string;projectId:string;clientId:string;onboardingId:string|null;stepId:string|null;templateId:string;templateVersionId:string;subject:string;status:ContractStatus;provider:string|null;providerDocumentId:string|null;signingUrl:string|null;expiresAt:string|null;generatedAt:string;sentAt:string|null;viewedAt:string|null;signedAt:string|null;declinedAt:string|null;createdAt:string;version:number;contractVersion:ContractVersion;recipients:ContractRecipient[];signatures:ContractSignature[] }
export interface ClientContractSummary { id:string;projectId:string;subject:string;status:ContractStatus;expiresAt:string|null;sentAt:string|null;signedAt:string|null;yourSignatureRequired:boolean }
export interface ClientContractDetail { id:string;projectId:string;subject:string;status:ContractStatus;title:string;legalContent:string;contentHash:string;expiresAt:string|null;sentAt:string|null;viewedAt:string|null;signedAt:string|null;yourSigningUrl:string|null;signedDocumentAvailable:boolean;recipients:ContractRecipient[];version:number }
export interface ContractDownload { url:string;expiresAt:string;filename:string }
