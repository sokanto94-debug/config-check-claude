package org.example.model;

/**
 * Ссылка на контур — пара «ветка + система» внутри репозитория.
 *
 * <p>Система соответствует директории верхнего уровня в репозитории,
 * содержащей поддиректории микросервисов с YAML-файлами.</p>
 *
 * @param branch ветка git-репозитория (например, {@code lt}, {@code preprod}).
 * @param system имя системы — директория первого уровня (например, {@code 15_10}).
 */
public record ContourRef(String branch, String system) {}
