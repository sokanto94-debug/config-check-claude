package org.example.model;

import java.util.List;

/**
 * Результат сканирования одного сервиса.
 *
 * @param service  имя сервиса (имя директории).
 * @param found    {@code true} если указанная ветка найдена в репозитории.
 * @param commits  список коммит-сообщений ветки (только коммиты, уникальные для этой ветки).
 * @param error    сообщение об ошибке; {@code null} если всё в порядке.
 */
public record ServiceScanResult(String service, boolean found, List<String> commits, String error) {}
