package org.example.model;

import java.util.List;

/**
 * Результат сравнения двух контуров.
 *
 * <p>Содержит полное дерево различий: контур → микросервис → файл → параметр,
 * а также сводные счётчики для отображения в UI.</p>
 *
 * @param left          левый контур сравнения.
 * @param right         правый контур сравнения.
 * @param services      список различий по микросервисам.
 * @param totalModified количество микросервисов со статусом {@code MODIFIED}.
 * @param totalAdded    количество микросервисов со статусом {@code ADDED}.
 * @param totalRemoved  количество микросервисов со статусом {@code REMOVED}.
 * @param totalSame     количество микросервисов со статусом {@code SAME}.
 */
public record CompareResponse(
        ContourRef left,
        ContourRef right,
        List<ServiceDiff> services,
        int totalModified,
        int totalAdded,
        int totalRemoved,
        int totalSame
) {}
