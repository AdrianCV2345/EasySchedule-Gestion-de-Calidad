package com.easyschedule.backend.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.easyschedule.backend.auth.service.SessionTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
@DisplayName("BearerTokenAuthenticationFilter - pruebas unitarias (HU8: filtro de autenticacion Bearer)")
class BearerTokenAuthenticationFilterTest {

    @Mock
    private SessionTokenService sessionTokenService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private BearerTokenAuthenticationFilter filter;

    @BeforeEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void limpiarContextoTrasPrueba() {
        SecurityContextHolder.clearContext();
    }

    private void stubearMetadatosPeticion() {
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/recurso");
    }

    @Test
    @DisplayName("FT-01 sin encabezado Authorization: no valida token, no autentica y continua la cadena")
    void sinAuthorizationNoValidaToken() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verifyNoInteractions(sessionTokenService);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("FT-02 encabezado no Bearer: no valida token, no autentica y continua la cadena")
    void encabezadoNoBearerIgnoraToken() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Basic YWJjOjEyMw==");

        filter.doFilterInternal(request, response, filterChain);

        verifyNoInteractions(sessionTokenService);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("FT-03 token valido y contexto vacio: establece la autenticacion con el userId")
    void tokenValidoContextoVacioEstableceAutenticacion() throws Exception {
        stubearMetadatosPeticion();
        when(request.getHeader("Authorization")).thenReturn("Bearer tokenvalido");
        when(sessionTokenService.validateAndGetUserId("tokenvalido")).thenReturn(Optional.of(1L));

        filter.doFilterInternal(request, response, filterChain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo("1");
        assertThat(authentication.getAuthorities()).isEmpty();
        verify(sessionTokenService).validateAndGetUserId("tokenvalido");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("FT-04 token valido y contexto ya autenticado: conserva el contexto existente")
    void tokenValidoContextoExistenteNoReemplaza() throws Exception {
        stubearMetadatosPeticion();
        Authentication existente = new UsernamePasswordAuthenticationToken("existente", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(existente);
        when(request.getHeader("Authorization")).thenReturn("Bearer tokenvalido");
        when(sessionTokenService.validateAndGetUserId("tokenvalido")).thenReturn(Optional.of(7L));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(existente);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("FT-05 token invalido corto: no autentica, usa referencia sin abreviar y continua")
    void tokenInvalidoCortoNoAutentica() throws Exception {
        stubearMetadatosPeticion();
        when(request.getHeader("Authorization")).thenReturn("Bearer abcdef");
        when(sessionTokenService.validateAndGetUserId("abcdef")).thenReturn(Optional.empty());

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("FT-06 token invalido largo: no autentica, abrevia la referencia y continua")
    void tokenInvalidoLargoNoAutentica() throws Exception {
        stubearMetadatosPeticion();
        when(request.getHeader("Authorization")).thenReturn("Bearer abcdefghijklmnop");
        when(sessionTokenService.validateAndGetUserId("abcdefghijklmnop")).thenReturn(Optional.empty());

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("FT-07 encabezado Bearer vacio: trata el token en blanco como invalido y continua")
    void bearerVacioNoAutentica() throws Exception {
        stubearMetadatosPeticion();
        when(request.getHeader("Authorization")).thenReturn("Bearer ");
        when(sessionTokenService.validateAndGetUserId("")).thenReturn(Optional.empty());

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }
}
