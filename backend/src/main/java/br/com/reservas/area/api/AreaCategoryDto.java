package br.com.reservas.area.api;

import br.com.reservas.area.domain.AreaCategory;
import br.com.reservas.area.domain.AreaCategoryTemplates;

/** `GET /area-categories` (docs/03, RF-ARE-03, RN-13). */
public record AreaCategoryDto(String code, String label, String rulesTemplate, String conductTemplate) {

    public static AreaCategoryDto from(AreaCategory category) {
        return new AreaCategoryDto(category.name(), category.label(), AreaCategoryTemplates.rulesTemplate(category),
            AreaCategoryTemplates.conductTemplate(category));
    }
}
