package org.example.model;

/**
 * Запрос на редактирование одного параметра в YAML-файле на диске.
 *
 * @param filePath относительный путь к файлу в репозитории (например, {@code 15_10/login/application.yaml}).
 * @param key      путь к параметру в нотации с точками (например, {@code server.port}).
 * @param newValue новое значение параметра.
 */
public record EditRequest(String filePath, String key, String newValue) {}
