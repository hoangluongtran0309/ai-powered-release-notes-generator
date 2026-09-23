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
    private final String uiLocale;

    ReleaseFlowPrincipal(AppUser user) {
        this.userId = user.getId();
        this.organizationId = user.getOrganizationId();
        this.displayName = user.getDisplayName();
        this.email = user.getEmail();
        this.passwordHash = user.passwordHash();
        this.role = user.getRole();
        this.uiLocale = user.getUiLocale();
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

    /**
     * The UI language saved on this account, or null when the browser decides. It is a
     * snapshot taken when the session began; a change also writes the language cookie,
     * which outranks it, so the person never waits for a new session to see it.
     */
    public String uiLocale() {
        return uiLocale;
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
