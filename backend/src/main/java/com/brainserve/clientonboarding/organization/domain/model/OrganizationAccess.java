package com.brainserve.clientonboarding.organization.domain.model;

import com.brainserve.clientonboarding.identity.domain.model.UserAccount;
import java.util.Set;
import java.util.UUID;

public record OrganizationAccess(
        Organization organization,
        UserAccount user,
        UUID membershipId,
        MembershipStatus membershipStatus,
        UUID roleId,
        String roleName,
        Set<String> permissions
) {
    public boolean isUsableInternalAccess() {
        return organization.status() == Organization.OrganizationStatus.ACTIVE
                && membershipStatus == MembershipStatus.ACTIVE
                && user.principalType() == UserAccount.PrincipalType.INTERNAL;
    }

    public enum MembershipStatus { INVITED, ACTIVE, SUSPENDED, ARCHIVED }
}
