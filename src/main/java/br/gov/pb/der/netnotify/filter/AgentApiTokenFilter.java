package br.gov.pb.der.netnotify.filter;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class AgentApiTokenFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(AgentApiTokenFilter.class);

    @Value("${app.agent-api.token:netnotify-agent-secret-change-me}")
    private String agentApiToken;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/agent-api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            logger.warn("[AgentApi] Requisição sem token: {} {}", request.getMethod(), request.getRequestURI());
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Token de agente ausente");
            return;
        }

        String token = authHeader.substring(7);
        if (!agentApiToken.equals(token)) {
            logger.warn("[AgentApi] Token inválido na requisição: {} {}", request.getMethod(), request.getRequestURI());
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Token de agente inválido");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
