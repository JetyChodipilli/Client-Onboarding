package com.brainserve.onboarding.onboarding.domain.model;

import com.brainserve.onboarding.client.domain.model.ClientProjectAccessLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "client_invitations", schema = "client_onboarding")
public class ClientInvitation {
    @Id private UUID id;
    @Column(name="organization_id", nullable=false) private UUID organizationId;
    @Column(name="onboarding_id", nullable=false) private UUID onboardingId;
    @Column(name="project_id", nullable=false) private UUID projectId;
    @Column(name="client_id", nullable=false) private UUID clientId;
    @Column(name="contact_id", nullable=false) private UUID contactId;
    @Column(nullable=false, length=320) private String email;
    @Column(name="normalized_email", nullable=false, length=320) private String normalizedEmail;
    @Column(name="display_name_snapshot", nullable=false, length=160) private String displayNameSnapshot;
    @Enumerated(EnumType.STRING) @Column(name="access_level", nullable=false, length=24) private ClientProjectAccessLevel accessLevel;
    @Column(name="token_hash", nullable=false, unique=true, length=64) private String tokenHash;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=24) private ClientInvitationStatus status;
    @Column(name="expires_at", nullable=false) private Instant expiresAt;
    @Column(name="last_sent_at", nullable=false) private Instant lastSentAt;
    @Column(name="resend_count", nullable=false) private int resendCount;
    @Column(name="invited_by", nullable=false) private UUID invitedBy;
    @Column(name="accepted_by_user_id") private UUID acceptedByUserId;
    @Column(name="accepted_at") private Instant acceptedAt;
    @Column(name="revoked_at") private Instant revokedAt;
    @Column(name="created_at", nullable=false) private Instant createdAt;
    @Column(name="updated_at", nullable=false) private Instant updatedAt;
    @Version private long version;

    protected ClientInvitation() {}

    public ClientInvitation(UUID id, UUID organizationId, UUID onboardingId, UUID projectId, UUID clientId,
                            UUID contactId, String email, String normalizedEmail, String displayNameSnapshot,
                            ClientProjectAccessLevel accessLevel, String tokenHash, Instant expiresAt,
                            UUID invitedBy, Instant now) {
        this.id=id; this.organizationId=organizationId; this.onboardingId=onboardingId; this.projectId=projectId;
        this.clientId=clientId; this.contactId=contactId; this.email=email; this.normalizedEmail=normalizedEmail;
        this.displayNameSnapshot=displayNameSnapshot; this.accessLevel=accessLevel; this.tokenHash=tokenHash;
        this.status=ClientInvitationStatus.PENDING; this.expiresAt=expiresAt; this.lastSentAt=now;
        this.invitedBy=invitedBy; this.createdAt=now; this.updatedAt=now;
    }

    public UUID getId(){return id;} public UUID getOrganizationId(){return organizationId;}
    public UUID getOnboardingId(){return onboardingId;} public UUID getProjectId(){return projectId;}
    public UUID getClientId(){return clientId;} public UUID getContactId(){return contactId;}
    public String getEmail(){return email;} public String getNormalizedEmail(){return normalizedEmail;}
    public String getDisplayNameSnapshot(){return displayNameSnapshot;} public ClientProjectAccessLevel getAccessLevel(){return accessLevel;}
    public String getTokenHash(){return tokenHash;} public ClientInvitationStatus getStatus(){return status;}
    public Instant getExpiresAt(){return expiresAt;} public Instant getLastSentAt(){return lastSentAt;}
    public int getResendCount(){return resendCount;} public UUID getInvitedBy(){return invitedBy;}
    public UUID getAcceptedByUserId(){return acceptedByUserId;} public Instant getAcceptedAt(){return acceptedAt;}
    public Instant getRevokedAt(){return revokedAt;} public Instant getCreatedAt(){return createdAt;}
    public Instant getUpdatedAt(){return updatedAt;} public long getVersion(){return version;}

    public boolean usable(Instant now){ return status==ClientInvitationStatus.PENDING && expiresAt.isAfter(now); }
    public void rotate(String newTokenHash, Instant newExpiry, Instant now){
        if(status!=ClientInvitationStatus.PENDING) throw new IllegalStateException("Only pending invitations can be resent");
        tokenHash=newTokenHash; expiresAt=newExpiry; lastSentAt=now; resendCount++; updatedAt=now;
    }
    public void revoke(Instant now){
        if(status!=ClientInvitationStatus.PENDING) throw new IllegalStateException("Only pending invitations can be revoked");
        status=ClientInvitationStatus.REVOKED; revokedAt=now; updatedAt=now;
    }
    public void accept(UUID userId, Instant now){
        if(!usable(now)) throw new IllegalStateException("Invitation is not usable");
        status=ClientInvitationStatus.ACCEPTED; acceptedByUserId=userId; acceptedAt=now; updatedAt=now;
    }
}
