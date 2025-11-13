package br.gov.pb.der.netnotify.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.gov.pb.der.netnotify.dto.DepartmentDto;
import br.gov.pb.der.netnotify.service.DepartmentService;
import br.gov.pb.der.netnotify.utils.Functions;
import br.gov.pb.der.netnotify.utils.SimpleResponseUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/departments")
@RequiredArgsConstructor
public class DepartmentController {

    public static final int DEFAULT_PAGE_SIZE = 10;

    private final DepartmentService departmentService;

    @PostMapping("/create")
    public ResponseEntity<SimpleResponseUtils<?>> saveMessage(@RequestBody @Valid DepartmentDto messageDto,
            BindingResult bindingResult) {

        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest()
                    .body(SimpleResponseUtils.error(null, Functions.errorStringfy(bindingResult)));
        }
        try {
            departmentService.saveFromDto(messageDto);
            return ResponseEntity.ok(SimpleResponseUtils.success(null, "Departamento salvo com sucesso."));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(SimpleResponseUtils.error(null, "Erro ao salvar a mensagem: " + e.getMessage()));
        }
    }

    @DeleteMapping(path = "/delete", params = "id")
    public ResponseEntity<SimpleResponseUtils<?>> deleteMessage(@RequestParam UUID id) {
        try {
            departmentService.deleteById(id);
            return ResponseEntity.ok(SimpleResponseUtils.success(null, "Departamento deletado com sucesso."));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(SimpleResponseUtils.error(null, "Erro ao deletar o departamento: " + e.getMessage()));
        }
    }

}
