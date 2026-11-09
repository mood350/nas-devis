package com.nasimmobilier.devis.quotes;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Meme contrat sur un VRAI PostgreSQL 16 (migrations Flyway comprises). Ignore automatiquement si Docker est absent :
 * dans ce cas rien n'est verifie sur PostgreSQL (voir README).
 */
@Testcontainers(disabledWithoutDocker = true)
class QuoteDbContractPostgresTest extends QuoteDbContractTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
