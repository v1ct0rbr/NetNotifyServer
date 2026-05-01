package br.gov.pb.der.netnotify.config;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String MESSAGE_TYPES = "messageTypes";
    public static final String LEVELS = "levels";
    public static final String DEPARTMENTS = "departments";
    public static final String AGENT_MESSAGES = "agentMessages";

    /**
     * ObjectMapper exclusivo para serialização Redis/Dragonfly.
     * Usa default typing para preservar informação de tipo no JSON,
     * necessário para deserialização correta de objetos polimórficos.
     * Mantido separado do ObjectMapper HTTP para não poluir respostas de API.
     */
    @Bean("redisCacheObjectMapper")
    public ObjectMapper redisCacheObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.activateDefaultTyping(
                mapper.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);
        return mapper;
    }

    /**
     * Serializador JSON compartilhado entre RedisTemplate e RedisCacheManager,
     * evitando divergência de configuração entre os dois usos do Redis.
     */
    @Bean
    public GenericJackson2JsonRedisSerializer redisCacheSerializer() {
        return new GenericJackson2JsonRedisSerializer(redisCacheObjectMapper());
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration baseConfig = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(redisCacheSerializer()))
                .disableCachingNullValues();

        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        // Tipos e níveis raramente mudam — TTL longo
        cacheConfigurations.put(MESSAGE_TYPES, baseConfig.entryTtl(Duration.ofHours(1)));
        cacheConfigurations.put(LEVELS, baseConfig.entryTtl(Duration.ofHours(1)));
        // Departamentos mudam com menos frequência que mensagens
        cacheConfigurations.put(DEPARTMENTS, baseConfig.entryTtl(Duration.ofMinutes(30)));
        // Respostas para agentes — TTL curto para garantir frescor entre polls
        cacheConfigurations.put(AGENT_MESSAGES, baseConfig.entryTtl(Duration.ofSeconds(30)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(baseConfig.entryTtl(Duration.ofMinutes(5)))
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }
}
