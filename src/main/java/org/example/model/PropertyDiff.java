package org.example.model;

/**
 * Результат сравнения одного YAML-параметра между двумя контурами.
 *
 * @param key        путь к параметру в нотации с точками (например, {@code server.port}
 *                   или {@code items[0].name}).
 * @param leftValue  значение параметра в левом контуре; {@code null}, если ключ отсутствует.
 * @param rightValue значение параметра в правом контуре; {@code null}, если ключ отсутствует.
 * @param type       тип изменения: {@code ADDED}, {@code REMOVED}, {@code MODIFIED} или {@code SAME}.
 */
public record PropertyDiff(String key, String leftValue, String rightValue, DiffType type) {}
