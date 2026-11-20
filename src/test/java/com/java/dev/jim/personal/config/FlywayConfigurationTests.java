package com.java.dev.jim.personal.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class FlywayConfigurationTests {
    @Test
    void missingMigrationFlagDoesNotAccessDatabase() {
        assertMigrationDisabled(new ApplicationContextRunner());
    }

    @Test
    void disabledMigrationFlagDoesNotAccessDatabase() {
        assertMigrationDisabled(new ApplicationContextRunner()
                .withPropertyValues("spring.flyway.enabled=false"));
    }

    private void assertMigrationDisabled(ApplicationContextRunner runner) {
        DataSource dataSource = mock(DataSource.class);
        runner.withUserConfiguration(FlywayConfiguration.class)
                .withBean(DataSource.class, () -> dataSource)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean("flywayMigration");
                    verifyNoInteractions(dataSource);
                });
    }
}
