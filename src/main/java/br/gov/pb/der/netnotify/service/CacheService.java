package br.gov.pb.der.netnotify.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CacheService {

    private Cache meuCache;

    private final CacheManager cacheManager;

    public void initialize(String value) {
        this.meuCache = cacheManager.getCache(value);
    }

    public void limparCache() {
        meuCache.clear();
    }

    public void evictSingleCacheValue(String cacheKey) {
        meuCache.evict(cacheKey);
    }

    public void evictAllCaches() {
        cacheManager.getCacheNames().stream()
                .forEach(cacheName -> {
                    Cache cache = cacheManager.getCache(cacheName);
                    if (cache != null) {
                        cache.clear();
                    }
                });
    }

    public List<String> clearAllCaches() {
        List<String> clearedCacheNames = new ArrayList<>();

        cacheManager.getCacheNames().forEach(cacheName -> {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
                clearedCacheNames.add(cacheName);
            }
        });

        return clearedCacheNames;
    }

    public void clearAllByValue(String value) {
        cacheManager.getCacheNames().stream()
                .filter(cacheName -> cacheName.contains(value))
                .forEach(cacheName -> {
                    Cache cache = cacheManager.getCache(cacheName);
                    if (cache != null) {
                        cache.clear();
                    }
                });
    }

    public void clearAllByValueAndKey(String value, String cacheKey) {
        cacheManager.getCacheNames().stream()
                .filter(cacheName -> cacheName.contains(value))
                .forEach(cacheName -> {
                    Cache cache = cacheManager.getCache(cacheName);
                    if (cache != null) {
                        cache.evict(cacheKey);
                    }
                });
    }

    public void clearAllByValues(String... values) {
        cacheManager.getCacheNames().stream()
                .filter(cacheName -> {
                    for (String value : values) {
                        if (cacheName.contains(value)) {
                            return true;
                        }
                    }
                    return false;
                })
                .forEach(cacheName -> {
                    Cache cache = cacheManager.getCache(cacheName);
                    if (cache != null) {
                        cache.clear();
                    }
                });
    }

}
