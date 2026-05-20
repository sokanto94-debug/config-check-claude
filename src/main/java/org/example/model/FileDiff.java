package org.example.model;

import java.util.List;

/**
 * Результат сравнения одного YAML-файла между двумя контурами.
 *
 * <p>Файл идентифицируется по имени (без пути к микросервису).
 * Содержит постатейный список отличий параметров.</p>
 *
 * @param fileName имя файла (например, {@code application.yaml}).
 * @param diffs    список различий по параметрам; пустой, если файл только добавлен или удалён.
 * @param status   агрегированный статус файла.
 */
public record FileDiff(String fileName, List<PropertyDiff> diffs, DiffType status) {}
