package org.example.model;

import java.util.Map;

/**
 * Результат сравнения одного YAML-параметра по нескольким микросервисам.
 *
 * @param key     путь к параметру в нотации с точками.
 * @param values  значения параметра по каждому микросервису: {@code microserviceName → value}.
 *                Значение {@code null} означает, что ключ отсутствует у данного микросервиса.
 * @param allSame {@code true}, если значение одинаково у всех микросервисов.
 */
public record MultiPropertyDiff(String key, Map<String, String> values, boolean allSame) {}
