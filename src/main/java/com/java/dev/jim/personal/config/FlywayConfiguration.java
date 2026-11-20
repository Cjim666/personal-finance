package com.java.dev.jim.personal.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
@ConditionalOnProperty(name = "spring.flyway.enabled", havingValue = "true")
public class FlywayConfiguration {
    @Bean("flywayMigration")
    Flyway flywayMigration(DataSource dataSource) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .baselineOnMigrate(false)
                .locations("classpath:db/migration/h2")
                .load();
        flyway.migrate();
        return flyway;
    }

    @Bean
    static BeanFactoryPostProcessor entityManagerFactoryDependsOnFlywayMigration() {
        return beanFactory -> beanFactory.getBeanDefinition("entityManagerFactory").setDependsOn("flywayMigration");
    }
}
