package com.hoangluongtran0309.releaseflow;

import com.hoangluongtran0309.releaseflow.support.PostgreSqlIntegrationTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class ReleaseFlowApplicationTest extends PostgreSqlIntegrationTest {

    @Autowired
    private Flyway flyway;

    @Test
    void applicationContextStarts() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("24");
    }
}
