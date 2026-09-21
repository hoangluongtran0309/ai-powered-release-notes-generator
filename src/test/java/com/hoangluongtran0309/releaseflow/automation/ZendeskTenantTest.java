package com.hoangluongtran0309.releaseflow.automation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** What a Zendesk action must say before it can write anywhere. */
class ZendeskTenantTest {

    @Test
    void readsASubdomainAsTheOnlyAddressItCanReach() {
        ZendeskTenant tenant = ZendeskTenant.from(configuration("  ACME  ", "releaseflow", "42", "99"));

        assertThat(tenant.origin()).isEqualTo("https://acme.zendesk.com");
        assertThat(tenant.clientId()).isEqualTo("releaseflow");
        assertThat(tenant.sectionId()).isEqualTo("42");
        assertThat(tenant.userSegmentId()).isEqualTo("99");
    }

    @Test
    void leavesAnArticleOpenWhenNoSegmentIsNamed() {
        assertThat(ZendeskTenant.from(configuration("acme", "releaseflow", "42", "  ")).userSegmentId()).isNull();
    }

    @ParameterizedTest
    @CsvSource({
            "'', releaseflow, 42, , automation_zendesk_subdomain_required",
            "acme.zendesk.com, releaseflow, 42, , automation_zendesk_subdomain_invalid",
            "https://acme.zendesk.com, releaseflow, 42, , automation_zendesk_subdomain_invalid",
            "-acme, releaseflow, 42, , automation_zendesk_subdomain_invalid",
            "acme-, releaseflow, 42, , automation_zendesk_subdomain_invalid",
            "ACME_TOOLS, releaseflow, 42, , automation_zendesk_subdomain_invalid",
            "acme, '', 42, , automation_zendesk_client_id_required",
            "acme, releaseflow, '', , automation_zendesk_section_required",
            "acme, releaseflow, 0, , automation_zendesk_section_invalid",
            "acme, releaseflow, -1, , automation_zendesk_section_invalid",
            "acme, releaseflow, section, , automation_zendesk_section_invalid",
            "acme, releaseflow, 42, private, automation_zendesk_user_segment_invalid",
            "acme, releaseflow, 42, 999999999999999999999999, automation_zendesk_user_segment_invalid"
    })
    void refusesWhatZendeskCouldNotUse(
            String subdomain,
            String clientId,
            String sectionId,
            String userSegmentId,
            String code
    ) {
        Map<String, String> configuration = configuration(subdomain, clientId, sectionId, userSegmentId);

        assertThatThrownBy(() -> ZendeskTenant.from(configuration))
                .isInstanceOf(AutomationActionInvalidException.class)
                .extracting(exception -> ((AutomationActionInvalidException) exception).code())
                .isEqualTo(code);
    }

    private static Map<String, String> configuration(
            String subdomain,
            String clientId,
            String sectionId,
            String userSegmentId
    ) {
        Map<String, String> configuration = new HashMap<>();
        configuration.put(ZendeskTenant.SUBDOMAIN_KEY, subdomain);
        configuration.put(ZendeskTenant.CLIENT_ID_KEY, clientId);
        configuration.put(ZendeskTenant.SECTION_KEY, sectionId);
        configuration.put(ZendeskTenant.USER_SEGMENT_KEY, userSegmentId);
        return configuration;
    }
}
