package org.example.model;

/**
 * Настройки подключения к git-репозиторию.
 *
 * <p>Должен быть указан ровно один из параметров: {@code url} или {@code localPath}.
 * Если оба заполнены, приоритет отдаётся {@code localPath}.</p>
 *
 * @param url       URL удалённого репозитория (https, git+ssh). Может быть {@code null}.
 * @param token     OAuth2-токен для доступа к приватному репозиторию. Может быть {@code null}.
 * @param localPath Абсолютный путь к локальному клону репозитория. Может быть {@code null}.
 */
public record RepoSettings(String url, String token, String localPath) {}
