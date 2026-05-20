package org.example.model;

import java.util.List;

/**
 * Результат сравнения одного микросервиса между двумя контурами.
 *
 * <p>Микросервис идентифицируется по имени директории второго уровня
 * (структура репозитория: {@code {system}/{microservice}/{file}.yaml}).</p>
 *
 * @param name   имя микросервиса.
 * @param files  список различий по файлам; пустой, если сервис только добавлен или удалён.
 * @param status агрегированный статус микросервиса.
 */
public record ServiceDiff(String name, List<FileDiff> files, DiffType status) {}
