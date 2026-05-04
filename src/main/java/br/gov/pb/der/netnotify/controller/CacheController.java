package br.gov.pb.der.netnotify.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.gov.pb.der.netnotify.service.CacheService;
import br.gov.pb.der.netnotify.utils.SimpleResponseUtils;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/admin/cache")
@RequiredArgsConstructor
public class CacheController {

    private final CacheService cacheService;

    @PostMapping("/clear")
    public ResponseEntity<SimpleResponseUtils<Map<String, Object>>> clearAllCaches() {
        List<String> clearedCaches = cacheService.clearAllCaches();
        Map<String, Object> payload = Map.of(
                "clearedCaches", clearedCaches,
                "clearedCount", clearedCaches.size());

        return ResponseEntity.ok(SimpleResponseUtils.success(
                payload,
                "Cache da aplicação limpo com sucesso."));
    }
}
