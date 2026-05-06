package br.gov.pb.der.netnotify.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RabbitVhostUtilsTest {

    @Test
    void keepsRegularVhostUntouched() {
        assertThat(RabbitVhostUtils.normalize("/")).isEqualTo("/");
        assertThat(RabbitVhostUtils.normalize("/netnotify")).isEqualTo("/netnotify");
    }

    @Test
    void convertsGitBashRootPathBackToSlashVhost() {
        assertThat(RabbitVhostUtils.normalize("C:/Program Files/Git/")).isEqualTo("/");
    }

    @Test
    void convertsGitBashNestedPathBackToRabbitVhost() {
        assertThat(RabbitVhostUtils.normalize("C:/Program Files/Git/netnotify")).isEqualTo("/netnotify");
    }
}
