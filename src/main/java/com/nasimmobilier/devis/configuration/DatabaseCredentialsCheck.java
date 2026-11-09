package com.nasimmobilier.devis.configuration;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Echoue des le demarrage si DB_USER / DB_PASSWORD manquent pour PostgreSQL.
 * Spring laisse sinon le texte "${DB_USER}" tel quel et l'envoie comme nom d'utilisateur.
 * Verifie les valeurs finales du DataSource (donc aussi celles surchargees par les tests).
 */
@Configuration(proxyBeanMethods = false)
public class DatabaseCredentialsCheck {

    @Bean
    static BeanPostProcessor databaseCredentialsPostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof DataSourceProperties p) {
                    check(p.getUrl(), p.getUsername(), p.getPassword());
                }
                return bean;
            }
        };
    }

    static void check(String url, String username, String password) {
        if (url == null || !url.startsWith("jdbc:postgresql:")) {
            return; // H2 des tests : identifiants vides autorises
        }
        require("DB_USER", username);
        require("DB_PASSWORD", password);
    }

    private static void require(String variable, String value) {
        if (value == null || value.isBlank() || value.startsWith("${")) {
            throw new IllegalStateException("Variable d'environnement " + variable
                    + " absente ou vide : definissez-la (IntelliJ : Run > Edit Configurations > Environment variables ;"
                    + " Docker Compose : fichier .env). Voir README.");
        }
    }
}
