package br.com.reservas.area.api;

import br.com.reservas.area.domain.AreaCategory;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** `GET /area-categories` (docs/03): todos os perfis autenticados. */
@RestController
@RequestMapping("/api/v1/area-categories")
public class AreaCategoryController {

    @GetMapping
    public List<AreaCategoryDto> list() {
        return java.util.Arrays.stream(AreaCategory.values()).map(AreaCategoryDto::from).toList();
    }
}
