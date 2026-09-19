package br.com.adapter.in.web.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Exige o token de acesso ({@code Authorization: Bearer ...}) em tudo que está em /api/v1, menos /auth e /saude. */
@Component
public class FiltroAutenticacao extends OncePerRequestFilter {

    public static final String ATRIBUTO_USUARIO_ID = "cuidamed.usuarioId";
    private static final String PREFIXO = "/api/v1/";

    private final JwtService jwt;

    public FiltroAutenticacao(JwtService jwt) {
        this.jwt = jwt;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String caminho = request.getRequestURI();
        return !caminho.startsWith(PREFIXO) || caminho.startsWith(PREFIXO + "auth/") || caminho.equals(PREFIXO + "saude");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String cabecalho = request.getHeader("Authorization");
        Optional<Integer> usuarioId = cabecalho != null && cabecalho.startsWith("Bearer ")
                ? jwt.validar(cabecalho.substring(7).trim())
                : Optional.empty();

        if (usuarioId.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"erro\":\"Entre novamente para continuar.\"}");
            return;
        }

        request.setAttribute(ATRIBUTO_USUARIO_ID, usuarioId.get());
        chain.doFilter(request, response);
    }
}
