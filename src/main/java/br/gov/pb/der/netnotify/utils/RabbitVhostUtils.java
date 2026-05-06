package br.gov.pb.der.netnotify.utils;

public final class RabbitVhostUtils {

    private RabbitVhostUtils() {
    }

    public static String normalize(String vhost) {
        if (vhost == null || vhost.isBlank()) {
            return "/";
        }

        String normalized = vhost.trim().replace('\\', '/');

        // Git Bash no Windows pode converter "/" para algo como "C:/Program Files/Git/".
        if (normalized.matches("^[A-Za-z]:/.*")) {
            int gitPrefixIndex = normalized.indexOf("/Git");
            if (gitPrefixIndex >= 0) {
                String suffix = normalized.substring(gitPrefixIndex + 4);
                if (suffix.isBlank() || "/".equals(suffix)) {
                    return "/";
                }
                return suffix.startsWith("/") ? suffix : "/" + suffix;
            }

            return "/";
        }

        return normalized;
    }
}
