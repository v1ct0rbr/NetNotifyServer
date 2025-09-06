package br.gov.pb.der.netnotify.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.stereotype.Service;

@Service
public class LdapService {

    @Autowired
    private LdapTemplate ldapTemplate;

    @Value("${ldap.baseDn}")
    private String ldapBaseDn;

    @Value("${ldap.searchFilter:(sAMAccountName={0})}")
    private String searchFilter;

    @Value("${ldap.allowedGroups:}")
    private String allowedGroups;

    @Value("${ldap.adminGroup:}")
    private String adminGroup;

    /**
     * Autentica usuário via Spring LDAP
     */
    public boolean authenticate(String username, String password) {
        String filter = searchFilter.replace("{0}", username);
        return ldapTemplate.authenticate(ldapBaseDn, filter, password);
    }

    /**
     * Busca os grupos do usuário autenticado via atributo memberOf
     */
    public java.util.List<String> getUserGroups(String username) {
        String filter = searchFilter.replace("{0}", username);
        return ldapTemplate.search(
                ldapBaseDn,
                filter,
                (org.springframework.ldap.core.AttributesMapper<java.util.List<String>>) attributes -> {
                    java.util.List<String> groups = new java.util.ArrayList<>();
                    javax.naming.directory.Attribute memberOf = attributes.get("memberOf");
                    if (memberOf != null) {
                        try {
                            for (int i = 0; i < memberOf.size(); i++) {
                                groups.add(memberOf.get(i).toString());
                            }
                        } catch (javax.naming.NamingException e) {
                            // log ou ignore
                        }
                    }
                    return groups;
                }).stream().flatMap(java.util.List::stream).distinct().toList();
    }

    /**
     * Resultado da autenticação LDAP com grupos
     */
    public static class AuthResult {
        public final boolean authenticated;
        public final boolean isAdmin;

        public AuthResult(boolean authenticated, boolean isAdmin) {
            this.authenticated = authenticated;
            this.isAdmin = isAdmin;
        }
    }

    /**
     * Autentica usuário e verifica grupos permitidos/admin
     * exemplo:
     * LDAP_ALLOWED_GROUPS="CN=NetNotify-User,OU=Groups,DC=example,DC=com;CN=NetNotify-Admin,OU=Groups,DC=example,DC=com"
     * LDAP_ADMIN_GROUP="CN=NetNotify-Admin,OU=Groups,DC=example,DC=com"
     */
    public AuthResult authenticateWithGroups(String username, String password) {
        if (!authenticate(username, password)) {
            return new AuthResult(false, false);
        }
        java.util.List<String> userGroups = getUserGroups(username);
        // allowedGroups pode ser separado por ';' ou ','
        java.util.List<String> allowed = java.util.Arrays.stream(allowedGroups.split(";"))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
        boolean inAllowed = userGroups.stream().anyMatch(g -> allowed.stream().anyMatch(g::equals));
        boolean isAdmin = adminGroup != null && !adminGroup.isEmpty()
                && userGroups.stream().anyMatch(g -> g.equals(adminGroup));
        if (!inAllowed) {
            return new AuthResult(false, false);
        }
        return new AuthResult(true, isAdmin);
    }

}
