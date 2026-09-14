package com.hoangluongtran0309.releaseflow.account;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InvitationTokenTest {

    @Test
    void generatesUrlSafe256BitTokens() {
        SecureRandom random = new SecureRandom();
        String first = InvitationToken.generate(random);
        String second = InvitationToken.generate(random);

        assertThat(first).matches("[A-Za-z0-9_-]{43}");
        assertThat(second).isNotEqualTo(first);
    }

    @Test
    void hashesDeterministicallyAsLowercaseHex() {
        String token = "abc_DEF-123";

        assertThat(InvitationToken.hash(token)).matches("[0-9a-f]{64}").isEqualTo(InvitationToken.hash(" " + token + " "));
        assertThat(InvitationToken.hash(token)).isNotEqualTo(InvitationToken.hash("abc_DEF-124"));
    }

    @Test
    void neverPrintsRawTokens() {
        String token = InvitationToken.generate(new SecureRandom());
        IssuedInvitation issued = new IssuedInvitation(
                UUID.randomUUID(), "teammate@example.com", "/accept-invite#token=" + token, Instant.now()
        );
        AcceptInvitationRequest accept = new AcceptInvitationRequest();
        accept.setToken(token);
        accept.setPassword("correct horse battery");
        InvitationTokenRequest inspect = new InvitationTokenRequest();
        inspect.setToken(token);

        assertThat(issued.toString()).doesNotContain(token).contains("[REDACTED]");
        assertThat(accept.toString()).doesNotContain(token).doesNotContain("correct horse battery");
        assertThat(inspect.toString()).doesNotContain(token);
    }
}
