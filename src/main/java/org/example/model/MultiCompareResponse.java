package org.example.model;

import java.util.List;

/**
 * Результат сравнения нескольких микросервисов внутри одной ветки.
 *
 * <p>Данные сгруппированы по файлам: каждый файл содержит таблицу параметров
 * с колонкой на каждый микросервис.</p>
 *
 * @param branch       ветка git-репозитория.
 * @param system       имя системы.
 * @param microservices список сравниваемых микросервисов (определяет порядок колонок в UI).
 * @param files        список результатов по файлам.
 */
public record MultiCompareResponse(String branch, String system, List<String> microservices, List<MultiFileDiff> files) {}
