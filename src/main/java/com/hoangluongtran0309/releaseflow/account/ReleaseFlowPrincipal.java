package com.hoangluongtran0309.releaseflow.account;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public final class ReleaseFlowPrincipal implements UserDetails {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID userId;
    private final UUID organizationId;
    private final String displayName;
    private final String email;
    private final String passwordHash;
    private final AppUserRole role;

    ReleaseFlowPrincipal(AppUser user) {
        this.userId = user.getId();
        this.organizationId = user.getOrganizationId();
        this.displayName = user.getDisplayName();
        this.email = user.getEmail();
        this.passwordHash = user.passwordHash();
        this.role = user.getRole();
    }

    public UUID userId() {
        return userId;
    }

    public UUID organizationId() {
        return organizationId;
    }

    public String displayName() {
        return displayName;
    }

    public AppUserRole role() {
        return role;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }
}
