package org.example.model;

import java.util.List;

/**
 * Запрос на сравнение двух контуров.
 *
 * @param left        левый контур (ветка + система).
 * @param right       правый контур (ветка + система).
 * @param excludeKeys список ключей, которые нужно исключить из сравнения.
 *                    Может быть {@code null} или пустым.
 */
public record CompareRequest(ContourRef left, ContourRef right, List<String> excludeKeys) {}
