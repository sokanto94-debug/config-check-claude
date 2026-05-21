package org.example.model;

import java.util.List;
import java.util.Map;

/**
 * Результат сравнения одного YAML-файла по нескольким микросервисам.
 *
 * @param fileName     имя файла (например, {@code application.yaml}).
 * @param servicePaths полные пути к файлу для каждого микросервиса (service → path).
 * @param properties   список сравнений по каждому параметру файла (union ключей всех сервисов).
 * @param hasChanges   {@code true}, если хотя бы один параметр различается между сервисами.
 */
public record MultiFileDiff(String fileName, Map<String, String> servicePaths, List<MultiPropertyDiff> properties, boolean hasChanges) {}
