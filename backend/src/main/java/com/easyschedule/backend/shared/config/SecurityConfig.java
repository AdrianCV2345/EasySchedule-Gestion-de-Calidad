package com.easyschedule.backend.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @SuppressWarnings ("java:S4502")
    @Bean
    public SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        BearerTokenAuthenticationFilter bearerTokenAuthenticationFilter
    ) throws Exception {
        http
            .cors(cors -> {})
            // CSRF is not required because authentication is handled with JWT Bearer tokens.
            // The application is stateless and does not rely on browser cookies.           
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll() 
                .requestMatchers(HttpMethod.POST,"/api/login").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/login/google").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/registro").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/estudiantes/registro").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/features").permitAll()
                .requestMatchers(HttpMethod.GET, "/health").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(bearerTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
    
        return http.build();
    }
}
