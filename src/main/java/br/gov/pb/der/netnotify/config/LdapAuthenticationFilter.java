package br.gov.pb.der.netnotify.config;

import java.io.IOException;
import java.util.Collections;
import java.util.Hashtable;

import javax.naming.AuthenticationException;
import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filtro simples de autenticação LDAP.
 *
 * Comportamento:
 * - Se o header Authorization não estiver presente ou não for Basic, deixa a
 * requisição seguir adiante.
 * - Se houver Basic auth, tenta realizar um bind simples contra o servidor LDAP
 * configurado.
 * - Em caso de sucesso, popula o SecurityContext com um Authentication contendo
 * ROLE_USER.
 * - Em caso de falha, retorna 401.
 *
 * Configurações esperadas (opcionais) em application.yaml/properties:
 * - ldap.url (ex: ldap://ldap.example.org:389)
 * - ldap.baseDn (ex: dc=example,dc=org) - usado se necessário para montar DN
 * baseado em pattern
 * - ldap.userDnPattern (ex: uid={0},ou=people) - {0} será substituído pelo nome
 * do usuário
 */
@Component
public class LdapAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(LdapAuthenticationFilter.class);

    @Value("${ldap.url:}")
    private String ldapUrl;

    @Value("${ldap.baseDn:}")
    private String baseDn;

    @Value("${ldap.userDnPattern:}")
    private String userDnPattern;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String auth = request.getHeader("Authorization");

        // Somente processa se houver Basic auth e configuração de LDAP disponível
        if (auth == null || !auth.toLowerCase().startsWith("basic ") || ldapUrl == null || ldapUrl.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        // Decodifica Basic -> username:password
        try {
            String base64Credentials = auth.substring(6).trim();
            String credentials = new String(java.util.Base64.getDecoder().decode(base64Credentials),
                    java.nio.charset.StandardCharsets.UTF_8);
            int idx = credentials.indexOf(':');
            if (idx <= 0) {
                unauthorized(response, "Bad credentials format");
                return;
            }

            String username = credentials.substring(0, idx);
            String password = credentials.substring(idx + 1);

            String bindDn = buildBindDn(username);

            if (authenticateAgainstLdap(bindDn, password)) {
                // Autenticação bem sucedida: popula SecurityContext com autoridade simples
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        username,
                        null,
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
                SecurityContextHolder.getContext().setAuthentication(authToken);
                filterChain.doFilter(request, response);
            } else {
                unauthorized(response, "Invalid credentials");
            }

        } catch (IllegalArgumentException e) {
            logger.debug("Erro ao decodificar Authorization header: {}", e.getMessage());
            unauthorized(response, "Invalid Authorization header");
        } catch (Exception e) {
            logger.error("Erro durante autenticação LDAP: {}", e.getMessage(), e);
            unauthorized(response, "Authentication error");
        }
    }

    private String buildBindDn(String username) {
        if (userDnPattern != null && !userDnPattern.isEmpty()) {
            return userDnPattern.replace("{0}", username) + (baseDn != null && !baseDn.isEmpty() ? "," + baseDn : "");
        }

        // Tenta usar user@domain se baseDn ausente
        return username + (baseDn != null && !baseDn.isEmpty() ? "," + baseDn : "");
    }

    private boolean authenticateAgainstLdap(String bindDn, String password) {
        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, ldapUrl);
        env.put(Context.SECURITY_AUTHENTICATION, "simple");
        env.put(Context.SECURITY_PRINCIPAL, bindDn);
        env.put(Context.SECURITY_CREDENTIALS, password);

        DirContext ctx = null;
        try {
            ctx = new InitialDirContext(env);
            return true;
        } catch (AuthenticationException ae) {
            logger.debug("Credenciais inválidas para {}: {}", bindDn, ae.getMessage());
            return false;
        } catch (NamingException ne) {
            logger.error("Erro de comunicação com LDAP {}: {}", ldapUrl, ne.getMessage());
            return false;
        } finally {
            if (ctx != null) {
                try {
                    ctx.close();
                } catch (NamingException ignore) {
                    // ignore
                }
            }
        }
    }

    private void unauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        String body = String.format("{\"error\":\"%s\"}", message);
        response.getWriter().write(body);
    }
}
