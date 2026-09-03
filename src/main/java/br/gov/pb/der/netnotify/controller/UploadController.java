package br.gov.pb.der.netnotify.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import br.gov.pb.der.netnotify.utils.SimpleResponseUtils;
import jakarta.servlet.http.HttpServletRequest;

@RestController
public class UploadController {

    private static final long MAX_IMAGE_SIZE = 20 * 1024 * 1024; // 20 MB

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "avif");

    @Value("${app.upload.dir:./uploads}")
    private String uploadDir;

    @PostMapping("/upload/image")
    public ResponseEntity<SimpleResponseUtils<String>> uploadImage(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(SimpleResponseUtils.error(null, "Nenhum arquivo enviado."));
        }
        if (file.getSize() > MAX_IMAGE_SIZE) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(SimpleResponseUtils.error(null, "Imagem excede o tamanho máximo de 20MB."));
        }

        String original = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";
        String ext = extensionOf(original);
        if (ext == null || !ALLOWED_EXTENSIONS.contains(ext)) {
            return ResponseEntity.badRequest()
                    .body(SimpleResponseUtils.error(null,
                            "Tipo de arquivo não permitido. Use png, jpg, jpeg, gif, webp, bmp, svg ou avif."));
        }

        try {
            Path dir = Paths.get(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            String filename = UUID.randomUUID() + "." + ext;
            file.transferTo(dir.resolve(filename).toFile());

            String url = buildUrl(request, "/uploads/" + filename);
            return ResponseEntity.ok(SimpleResponseUtils.success(url, "Imagem enviada com sucesso."));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(SimpleResponseUtils.error(null, "Erro ao salvar a imagem: " + e.getMessage()));
        }
    }

    private String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return null;
        }
        return filename.substring(dot + 1).toLowerCase();
    }

    private String buildUrl(HttpServletRequest request, String path) {
        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();
        String contextPath = request.getContextPath();

        String base = scheme + "://" + host;
        if (!((scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443))) {
            base += ":" + port;
        }
        return base + (contextPath == null ? "" : contextPath) + path;
    }
}
