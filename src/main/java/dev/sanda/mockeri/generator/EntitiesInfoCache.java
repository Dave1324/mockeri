package dev.sanda.mockeri.generator;

import lombok.Getter;

import javax.lang.model.element.TypeElement;
import java.util.HashMap;
import java.util.Map;
@Getter
public class EntitiesInfoCache {
    private Map<String, TypeElement> typeElementMap = new HashMap<>();
}
