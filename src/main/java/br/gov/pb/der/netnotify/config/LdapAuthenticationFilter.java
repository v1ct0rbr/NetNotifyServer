package br.gov.pb.der.netnotify.config;

import java.io.IOException;
import java.util.Collections;
import java.util.Hashtable;

import javax.naming.AuthenticationException;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

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

    @Value("${ldap.managerDn:}")
    private String managerDn;

    @Value("${ldap.managerPassword:}")
    private String managerPassword;
    
    @Value("${ldap.searchFilter:(sAMAccountName={0})}")
    private String searchFilter;
    
    @Value("${ldap.authenticationContainers:}")
    private String authenticationContainers; // e.g. CN=Users;OU=Staff

    @Value("${ldap.searchScope:SUBTREE}")
    private String searchScope; // SUBTREE or ONELEVEL or OBJECT

    @Value("${ldap.timeoutSeconds:25}")
    private int timeoutSeconds;

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

            // Resolve DN: try userDnPattern first; if not present or fails, and a manager account
            // is configured, use it to search for the user's DN in the directory then bind.
            String bindDn = null;
            if (userDnPattern != null && !userDnPattern.isEmpty()) {
                bindDn = buildBindDn(username);
            }

            if (bindDn == null || bindDn.isEmpty()) {
                bindDn = findUserDn(username);
            }

            if (bindDn == null || bindDn.isEmpty()) {
                unauthorized(response, "User DN not found");
                return;
            }

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

    /**
     * If configured, uses the manager DN to search for the user's DN using the
     * ldap.searchFilter and baseDn defined in application properties.
     */
    private String findUserDn(String username) {
        if (managerDn == null || managerDn.isEmpty() || managerPassword == null) {
            return null;
        }

        // Build environment to bind as manager and perform search
    Hashtable<String, String> env = new Hashtable<>();
    env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
    env.put(Context.PROVIDER_URL, ldapUrl);
    env.put(Context.SECURITY_AUTHENTICATION, "simple");
    env.put(Context.SECURITY_PRINCIPAL, managerDn);
    env.put(Context.SECURITY_CREDENTIALS, managerPassword);
    // force LDAP v3
    env.put("java.naming.ldap.version", "3");
    // timeouts in milliseconds
    int toMs = Math.max(0, timeoutSeconds) * 1000;
    env.put("com.sun.jndi.ldap.connect.timeout", String.valueOf(toMs));
    env.put("com.sun.jndi.ldap.read.timeout", String.valueOf(toMs));

        DirContext ctx = null;
        try {
            ctx = new InitialDirContext(env);

            String filter = this.searchFilter;
            if (filter == null || filter.isEmpty()) {
                filter = "(sAMAccountName={0})";
            }
            filter = filter.replace("{0}", username);

            SearchControls sc = new SearchControls();
            int scope = SearchControls.SUBTREE_SCOPE;
            if ("ONELEVEL".equalsIgnoreCase(searchScope)) {
                scope = SearchControls.ONELEVEL_SCOPE;
            } else if ("OBJECT".equalsIgnoreCase(searchScope)) {
                scope = SearchControls.OBJECT_SCOPE;
            }
            sc.setSearchScope(scope);

            // If authenticationContainers is configured (e.g. CN=Users), prepend it to baseDn
            String searchBase = baseDn != null ? baseDn : "";
            if (authenticationContainers != null && !authenticationContainers.isEmpty()) {
                // take the first container if multiple separated by ';' or ','
                String first = authenticationContainers.split("[;,]")[0].trim();
                if (!first.isEmpty()) {
                    searchBase = first + (searchBase != null && !searchBase.isEmpty() ? "," + searchBase : "");
                }
            }

            NamingEnumeration<SearchResult> results = ctx.search(searchBase != null && !searchBase.isEmpty() ? searchBase : "", filter, sc);
            if (results != null && results.hasMore()) {
                SearchResult sr = results.next();
                return sr.getNameInNamespace();
            }
            return null;
        } catch (NamingException ne) {
            logger.error("Erro ao buscar usuário no LDAP {}: {}", ldapUrl, ne.getMessage());
            return null;
        } finally {
            if (ctx != null) {
                try {
                    ctx.close();
                } catch (NamingException ignore) {
                }
            }
        }
    }
}
