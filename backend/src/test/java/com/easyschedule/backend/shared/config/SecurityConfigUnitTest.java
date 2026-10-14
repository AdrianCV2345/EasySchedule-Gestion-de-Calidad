package com.easyschedule.backend.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Suite de Pruebas Unitarias con JUnit 5, Mockito y AssertJ para la configuracion de
 * seguridad (HU de login/registro).
 *
 * Valida los caminos independientes de SecurityConfig:
 * - passwordEncoder(): crea y retorna un BCryptPasswordEncoder.
 * - securityFilterChain(...): configura CORS, CSRF, sesion STATELESS, autorizacion,
 *   incorpora el filtro Bearer y construye la cadena.
 */
@DisplayName("SecurityConfig - Pruebas unitarias de los caminos independientes")
class SecurityConfigUnitTest {

    private final SecurityConfig securityConfig = new SecurityConfig();

    @Test
    @DisplayName("CP01: passwordEncoder() retorna una instancia no nula de BCryptPasswordEncoder")
    void cp01_passwordEncoder_retornaBCrypt() {
        PasswordEncoder encoder = securityConfig.passwordEncoder();

        assertThat(encoder).isNotNull();
        assertThat(encoder).isInstanceOf(BCryptPasswordEncoder.class);
    }

    @Test
    @DisplayName("CP02: securityFilterChain(...) construye la cadena e incorpora el filtro Bearer")
    void cp02_securityFilterChain_construyeCadenaConFiltroBearer() throws Exception {
        BearerTokenAuthenticationFilter bearerFilter = mock(BearerTokenAuthenticationFilter.class);
        try (AnnotationConfigApplicationContext context = corsContext()) {
            HttpSecurity http = newHttpSecurity(context);

            SecurityFilterChain chain = securityConfig.securityFilterChain(http, bearerFilter);

            assertThat(chain).isNotNull();
            assertThat(chain.getFilters()).contains(bearerFilter);
        }
    }

    private AnnotationConfigApplicationContext corsContext() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean("corsConfigurationSource", CorsConfigurationSource.class,
            (java.util.function.Supplier<CorsConfigurationSource>) UrlBasedCorsConfigurationSource::new);
        context.refresh();
        return context;
    }

    private HttpSecurity newHttpSecurity(ApplicationContext context) throws Exception {
        Map<Class<?>, Object> sharedObjects = new HashMap<>();
        sharedObjects.put(ApplicationContext.class, context);
        return new HttpSecurity(
            ObjectPostProcessor.identity(),
            new AuthenticationManagerBuilder(ObjectPostProcessor.identity()),
            sharedObjects);
    }
}
