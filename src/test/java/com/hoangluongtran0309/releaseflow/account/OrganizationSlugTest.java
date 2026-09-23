package com.hoangluongtran0309.releaseflow.account;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrganizationSlugTest {

    @Test
    void acceptsOneDnsLabelAndNothingElse() {
        assertThat(OrganizationSlug.parse("acme").value()).isEqualTo("acme");
        assertThat(OrganizationSlug.parse("  ACME-Tools  ").value()).isEqualTo("acme-tools");
        assertThat(OrganizationSlug.parse("a1").value()).isEqualTo("a1");

        assertThatThrownBy(() -> OrganizationSlug.parse("acme.example"))
                .isInstanceOf(InvalidOrganizationSlugException.class);
        assertThatThrownBy(() -> OrganizationSlug.parse("-acme"))
                .isInstanceOf(InvalidOrganizationSlugException.class);
        assertThatThrownBy(() -> OrganizationSlug.parse("acme-"))
                .isInstanceOf(InvalidOrganizationSlugException.class);
        assertThatThrownBy(() -> OrganizationSlug.parse("acme tools"))
                .isInstanceOf(InvalidOrganizationSlugException.class);
        assertThatThrownBy(() -> OrganizationSlug.parse("a".repeat(64)))
                .isInstanceOf(InvalidOrganizationSlugException.class);
        assertThatThrownBy(() -> OrganizationSlug.parse("  "))
                .isInstanceOf(InvalidOrganizationSlugException.class);
    }

    @Test
    void makesOneFromAnOrganizationsName() {
        assertThat(OrganizationSlug.fromName("Acme Tools").value()).isEqualTo("acme-tools");
        assertThat(OrganizationSlug.fromName("  ACME  ").value()).isEqualTo("acme");
        assertThat(OrganizationSlug.fromName("Acme, Inc.").value()).isEqualTo("acme-inc");
        // A name with nothing usable in it still has to answer somewhere.
        assertThat(OrganizationSlug.fromName("公司").value()).isEqualTo("org");
        assertThat(OrganizationSlug.fromName("").value()).isEqualTo("org");
        assertThat(OrganizationSlug.fromName(null).value()).isEqualTo("org");

        String long63 = OrganizationSlug.fromName("x".repeat(200)).value();
        assertThat(long63).hasSize(OrganizationSlug.MAX_LENGTH);
        assertThat(OrganizationSlug.parse(long63)).isNotNull();
    }

    @Test
    void numbersTheOnesThatComeAfter() {
        assertThat(OrganizationSlug.fromName("Acme").numbered(2).value()).isEqualTo("acme-2");

        // Even at the limit the numbered slug is still one valid label.
        String numbered = OrganizationSlug.fromName("x".repeat(80)).numbered(17).value();
        assertThat(numbered).hasSize(OrganizationSlug.MAX_LENGTH).endsWith("-17");
        assertThat(OrganizationSlug.parse(numbered)).isNotNull();
    }
}
