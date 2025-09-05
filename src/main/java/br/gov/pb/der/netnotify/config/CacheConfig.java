package br.gov.pb.der.netnotify.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {
    
    public static final String MESSAGES = "messageTypes";
    public static final String LEVELS = "levels";    

    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager(
            MESSAGES,
            LEVELS
            // Adicione outras chaves conforme necessário
        );
    }
}
