package org.example.model;

import java.util.List;

/**
 * Запрос на сравнение нескольких микросервисов внутри одной ветки.
 *
 * @param branch       ветка git-репозитория.
 * @param system       имя системы (директория первого уровня).
 * @param microservices список микросервисов для сравнения; если {@code null} или пустой —
 *                      сравниваются все микросервисы системы.
 */
public record ServiceCompareRequest(String branch, String system, List<String> microservices) {}
