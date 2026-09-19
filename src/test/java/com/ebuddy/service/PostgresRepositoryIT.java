package com.ebuddy.service;
import org.junit.jupiter.api.*; import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest; import org.springframework.test.context.DynamicPropertyRegistry; import org.springframework.test.context.DynamicPropertySource; import org.testcontainers.containers.PostgreSQLContainer; import org.testcontainers.junit.jupiter.Container; import org.testcontainers.junit.jupiter.Testcontainers;
@Testcontainers @DataJpaTest class PostgresRepositoryIT {
 @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("ebuddy").withUsername("ebuddy").withPassword("ebuddy");
 @DynamicPropertySource static void db(DynamicPropertyRegistry r){r.add("spring.datasource.url",POSTGRES::getJdbcUrl);r.add("spring.datasource.username",POSTGRES::getUsername);r.add("spring.datasource.password",POSTGRES::getPassword);r.add("spring.flyway.enabled",()->false);r.add("spring.jpa.hibernate.ddl-auto",()->"none");}
 @Test void containerStarts(){Assertions.assertTrue(POSTGRES.isRunning());}
}
