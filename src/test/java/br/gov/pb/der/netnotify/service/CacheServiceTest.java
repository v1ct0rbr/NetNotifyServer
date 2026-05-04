package br.gov.pb.der.netnotify.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

class CacheServiceTest {

    @Test
    void clearAllCachesClearsEachRegisteredCacheAndReturnsNames() {
        CacheManager cacheManager = mock(CacheManager.class);
        Cache levelsCache = mock(Cache.class);
        Cache departmentsCache = mock(Cache.class);

        when(cacheManager.getCacheNames()).thenReturn(List.of("levels", "departments"));
        when(cacheManager.getCache("levels")).thenReturn(levelsCache);
        when(cacheManager.getCache("departments")).thenReturn(departmentsCache);

        CacheService cacheService = new CacheService(cacheManager);

        List<String> clearedCaches = cacheService.clearAllCaches();

        assertThat(clearedCaches).containsExactly("levels", "departments");
        verify(levelsCache).clear();
        verify(departmentsCache).clear();
    }
}
