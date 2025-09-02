package br.gov.pb.der.netnotify.utils;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityUtils {

    public static String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated()) {
            return authentication.getName(); // Retorna o username
        }

        return null; // Ou lançar uma exceção se preferir
    }

    public static String getAuthorities() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated()) {
            return authentication.getAuthorities().toString(); // Retorna as authorities
        }

        return null; // Ou lançar uma exceção se preferir
    }
}