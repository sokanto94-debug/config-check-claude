package org.example.model;

/**
 * Запрос на сканирование папки с компонентами.
 *
 * @param folderPath абсолютный путь к папке, содержащей git-репозитории сервисов.
 * @param branch     имя ветки, которую нужно найти в каждом репозитории.
 * @param baseBranch базовая ветка для ограничения истории (например, {@code main}).
 *                   Если {@code null} — определяется автоматически (main / master / develop).
 */
public record ScanRequest(String folderPath, String branch, String baseBranch) {}
