package com.nasimmobilier.devis.configuration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DatabaseCredentialsCheckTest {

    private static final String PG = "jdbc:postgresql://localhost:5432/nasimmobilier";

    @Test
    void validCredentialsPass() {
        assertThatCode(() -> DatabaseCredentialsCheck.check(PG, "nasim", "secret")).doesNotThrowAnyException();
    }

    @Test
    void missingUserNamesTheVariable() {
        assertThatThrownBy(() -> DatabaseCredentialsCheck.check(PG, null, "secret")).hasMessageContaining("DB_USER");
        assertThatThrownBy(() -> DatabaseCredentialsCheck.check(PG, "${DB_USER}", "secret")).hasMessageContaining("DB_USER");
    }

    @Test
    void missingPasswordNamesTheVariable() {
        assertThatThrownBy(() -> DatabaseCredentialsCheck.check(PG, "nasim", null)).hasMessageContaining("DB_PASSWORD");
        assertThatThrownBy(() -> DatabaseCredentialsCheck.check(PG, "nasim", "${DB_PASSWORD}")).hasMessageContaining("DB_PASSWORD");
    }

    @Test
    void emptyValuesAreRejected() {
        assertThatThrownBy(() -> DatabaseCredentialsCheck.check(PG, "", "secret")).hasMessageContaining("DB_USER");
        assertThatThrownBy(() -> DatabaseCredentialsCheck.check(PG, "nasim", " ")).hasMessageContaining("DB_PASSWORD");
    }

    @Test
    void h2WithEmptyPasswordIsAllowed() {
        assertThatCode(() -> DatabaseCredentialsCheck.check("jdbc:h2:mem:x", "sa", "")).doesNotThrowAnyException();
    }
}
