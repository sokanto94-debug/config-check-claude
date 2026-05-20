package org.example.service;

import org.example.model.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.stream.*;

/**
 * Сервис сравнения конфигурационных файлов между контурами и микросервисами.
 *
 * <p>Предполагает следующую структуру репозитория:</p>
 * <pre>
 * {system}/
 *   {microservice}/
 *     application.yaml
 *     custom.yaml
 *     ...
 * </pre>
 *
 * <p>Поддерживает два режима сравнения:</p>
 * <ul>
 *   <li>{@link #compare} — сравнивает одну и ту же систему между двумя ветками.</li>
 *   <li>{@link #compareServices} — сравнивает несколько микросервисов внутри одной ветки.</li>
 * </ul>
 *
 * <p>В обоих случаях используется объединение (union) ключей и файлов:
 * отсутствующие значения отображаются как {@code null}.</p>
 */
@Service
public class ComparisonService {

    private final GitService gitService;
    private final YamlService yamlService;

    public ComparisonService(GitService gitService, YamlService yamlService) {
        this.gitService = gitService;
        this.yamlService = yamlService;
    }

    /**
     * Возвращает список систем (директорий верхнего уровня) в указанной ветке.
     *
     * <p>Системой считается директория, под которой есть хотя бы один YAML-файл
     * на глубине ≥ 2 уровней (т.е. в поддиректории микросервиса).</p>
     *
     * @param branch имя ветки.
     * @return отсортированный список уникальных имён систем.
     * @throws IOException при ошибке чтения репозитория.
     */
    public List<String> getSystems(String branch) throws IOException {
        return gitService.listYamlFiles(branch).stream()
                .filter(p -> p.chars().filter(c -> c == '/').count() >= 2)
                .map(p -> p.split("/")[0])
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * Возвращает список микросервисов системы в указанной ветке.
     *
     * <p>Микросервисом считается директория второго уровня ({@code system/microservice/}),
     * содержащая хотя бы один YAML-файл.</p>
     *
     * @param branch имя ветки.
     * @param system имя системы.
     * @return отсортированный список уникальных имён микросервисов.
     * @throws IOException при ошибке чтения репозитория.
     */
    public List<String> getMicroservices(String branch, String system) throws IOException {
        String prefix = system + "/";
        return gitService.listYamlFiles(branch).stream()
                .filter(p -> p.startsWith(prefix))
                .map(p -> p.substring(prefix.length()))
                .filter(p -> p.contains("/"))
                .map(p -> p.split("/")[0])
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * Сравнивает два контура и возвращает полное дерево различий.
     *
     * <p>Сравниваются все микросервисы, найденные хотя бы в одном из контуров.
     * Ключи, перечисленные в {@code excludeKeys}, исключаются из сравнения параметров.</p>
     *
     * @param left        левый контур (ветка + система).
     * @param right       правый контур (ветка + система).
     * @param excludeKeys множество ключей, которые не нужно сравнивать.
     * @return объект {@link CompareResponse} с полным деревом различий и счётчиками.
     * @throws IOException при ошибке чтения репозитория.
     */
    public CompareResponse compare(ContourRef left, ContourRef right, Set<String> excludeKeys) throws IOException {
        List<String> leftFiles = gitService.listYamlFiles(left.branch());
        List<String> rightFiles = gitService.listYamlFiles(right.branch());

        List<String> leftServices = extractServices(leftFiles, left.system());
        List<String> rightServices = extractServices(rightFiles, right.system());

        Set<String> allServices = new TreeSet<>(leftServices);
        allServices.addAll(rightServices);

        List<ServiceDiff> diffs = new ArrayList<>();
        int modified = 0, added = 0, removed = 0, same = 0;

        for (String service : allServices) {
            boolean inLeft = leftServices.contains(service);
            boolean inRight = rightServices.contains(service);

            if (!inLeft) {
                diffs.add(new ServiceDiff(service, List.of(), DiffType.ADDED));
                added++;
            } else if (!inRight) {
                diffs.add(new ServiceDiff(service, List.of(), DiffType.REMOVED));
                removed++;
            } else {
                ServiceDiff sd = compareService(left, right, service, leftFiles, rightFiles, excludeKeys);
                diffs.add(sd);
                if (sd.status() == DiffType.MODIFIED) modified++;
                else same++;
            }
        }

        return new CompareResponse(left, right, diffs, modified, added, removed, same);
    }

    /**
     * Извлекает список микросервисов из полного списка путей репозитория.
     *
     * @param paths  все пути YAML-файлов ветки.
     * @param system имя системы для фильтрации.
     * @return отсортированный список уникальных имён микросервисов.
     */
    private List<String> extractServices(List<String> paths, String system) {
        String prefix = system + "/";
        return paths.stream()
                .filter(p -> p.startsWith(prefix))
                .map(p -> p.substring(prefix.length()))
                .filter(p -> p.contains("/"))
                .map(p -> p.split("/")[0])
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * Сравнивает один микросервис между двумя контурами на уровне файлов и параметров.
     *
     * @param left          левый контур.
     * @param right         правый контур.
     * @param service       имя микросервиса.
     * @param leftAllFiles  все пути YAML-файлов левой ветки.
     * @param rightAllFiles все пути YAML-файлов правой ветки.
     * @param excludeKeys   ключи, исключённые из сравнения.
     * @return {@link ServiceDiff} со статусом и списком различий по файлам.
     * @throws IOException при ошибке чтения содержимого файлов.
     */
    private ServiceDiff compareService(ContourRef left, ContourRef right, String service,
                                        List<String> leftAllFiles, List<String> rightAllFiles,
                                        Set<String> excludeKeys) throws IOException {
        String leftPrefix = left.system() + "/" + service + "/";
        String rightPrefix = right.system() + "/" + service + "/";

        Map<String, String> leftFileMap = extractDirectFiles(leftAllFiles, leftPrefix);
        Map<String, String> rightFileMap = extractDirectFiles(rightAllFiles, rightPrefix);

        Set<String> allFileNames = new TreeSet<>(leftFileMap.keySet());
        allFileNames.addAll(rightFileMap.keySet());

        List<FileDiff> fileDiffs = new ArrayList<>();
        boolean anyDiff = false;

        for (String fileName : allFileNames) {
            String leftPath = leftFileMap.get(fileName);
            String rightPath = rightFileMap.get(fileName);

            if (leftPath == null) {
                fileDiffs.add(new FileDiff(fileName, List.of(), DiffType.ADDED));
                anyDiff = true;
            } else if (rightPath == null) {
                fileDiffs.add(new FileDiff(fileName, List.of(), DiffType.REMOVED));
                anyDiff = true;
            } else {
                String leftContent = gitService.readFile(left.branch(), leftPath);
                String rightContent = gitService.readFile(right.branch(), rightPath);
                Map<String, String> leftProps = yamlService.flatten(leftContent);
                Map<String, String> rightProps = yamlService.flatten(rightContent);
                List<PropertyDiff> propDiffs = diffProperties(leftProps, rightProps, excludeKeys);
                boolean hasDiff = propDiffs.stream().anyMatch(d -> d.type() != DiffType.SAME);
                fileDiffs.add(new FileDiff(fileName, propDiffs, hasDiff ? DiffType.MODIFIED : DiffType.SAME));
                if (hasDiff) anyDiff = true;
            }
        }

        return new ServiceDiff(service, fileDiffs, anyDiff ? DiffType.MODIFIED : DiffType.SAME);
    }

    /**
     * Строит отображение {@code fileName → fullPath} для «прямых» YAML-файлов под префиксом.
     *
     * <p>«Прямыми» считаются файлы, лежащие непосредственно в директории микросервиса
     * (без вложенных поддиректорий). Файлы в поддиректориях пропускаются.</p>
     *
     * @param paths  все пути YAML-файлов ветки.
     * @param prefix путь к директории микросервиса (с завершающим {@code /}).
     * @return упорядоченный словарь {@code имяФайла → полныйПуть}.
     */
    private Map<String, String> extractDirectFiles(List<String> paths, String prefix) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String path : paths) {
            if (path.startsWith(prefix)) {
                String rest = path.substring(prefix.length());
                if (!rest.contains("/")) {
                    result.put(rest, path);
                }
            }
        }
        return result;
    }

    /**
     * Возвращает объединённый список всех ключей системы в указанной ветке.
     *
     * <p>Используется для формирования списка доступных ключей при настройке
     * исключений. Обходит все микросервисы и все их прямые YAML-файлы.</p>
     *
     * @param branch имя ветки.
     * @param system имя системы.
     * @return отсортированный список уникальных ключей (в нотации с точками).
     * @throws IOException при ошибке чтения репозитория.
     */
    public List<String> getAllKeys(String branch, String system) throws IOException {
        List<String> allFiles = gitService.listYamlFiles(branch);
        List<String> services = getMicroservices(branch, system);
        Set<String> allKeys = new TreeSet<>();
        for (String svc : services) {
            String prefix = system + "/" + svc + "/";
            Map<String, String> fileMap = extractDirectFiles(allFiles, prefix);
            for (Map.Entry<String, String> entry : fileMap.entrySet()) {
                String content = gitService.readFile(branch, entry.getValue());
                allKeys.addAll(yamlService.flatten(content).keySet());
            }
        }
        return new ArrayList<>(allKeys);
    }

    /**
     * Сравнивает несколько микросервисов внутри одной ветки.
     *
     * <p>Результат сгруппирован по файлам. Для каждого файла строится таблица
     * параметров, где каждый столбец соответствует одному микросервису.
     * Используется объединение (union) файлов и ключей: если у сервиса нет
     * файла или ключа, его значение равно {@code null}.</p>
     *
     * @param branch       имя ветки.
     * @param system       имя системы.
     * @param requested    список микросервисов для сравнения; если {@code null} или пустой —
     *                     используются все микросервисы системы.
     * @return {@link MultiCompareResponse} с таблицами различий по файлам.
     * @throws IOException при ошибке чтения репозитория.
     */
    public MultiCompareResponse compareServices(String branch, String system, List<String> requested) throws IOException {
        List<String> services = (requested == null || requested.isEmpty())
                ? getMicroservices(branch, system)
                : requested;

        List<String> allFiles = gitService.listYamlFiles(branch);

        // service -> fileName -> flattenedProps
        Map<String, Map<String, Map<String, String>>> svcData = new LinkedHashMap<>();
        for (String svc : services) {
            String prefix = system + "/" + svc + "/";
            Map<String, String> fileMap = extractDirectFiles(allFiles, prefix);
            Map<String, Map<String, String>> fileProps = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : fileMap.entrySet()) {
                String content = gitService.readFile(branch, entry.getValue());
                fileProps.put(entry.getKey(), yamlService.flatten(content));
            }
            svcData.put(svc, fileProps);
        }

        Set<String> allFileNames = new TreeSet<>();
        for (Map<String, Map<String, String>> fileProps : svcData.values()) {
            allFileNames.addAll(fileProps.keySet());
        }

        List<MultiFileDiff> fileDiffs = new ArrayList<>();
        for (String fileName : allFileNames) {
            Set<String> allKeys = new TreeSet<>();
            for (String svc : services) {
                allKeys.addAll(svcData.get(svc).getOrDefault(fileName, Map.of()).keySet());
            }

            List<MultiPropertyDiff> propDiffs = new ArrayList<>();
            for (String key : allKeys) {
                Map<String, String> values = new LinkedHashMap<>();
                for (String svc : services) {
                    values.put(svc, svcData.get(svc).getOrDefault(fileName, Map.of()).get(key));
                }
                boolean allSame = new HashSet<>(values.values()).size() == 1;
                propDiffs.add(new MultiPropertyDiff(key, values, allSame));
            }

            boolean hasChanges = propDiffs.stream().anyMatch(p -> !p.allSame());
            fileDiffs.add(new MultiFileDiff(fileName, propDiffs, hasChanges));
        }

        return new MultiCompareResponse(branch, system, services, fileDiffs);
    }

    /**
     * Вычисляет различия между двумя плоскими словарями параметров.
     *
     * <p>Возвращает записи для всех ключей из объединения обоих словарей,
     * кроме ключей из {@code excludeKeys}.</p>
     *
     * @param left        параметры левого контура.
     * @param right       параметры правого контура.
     * @param excludeKeys ключи, которые нужно пропустить.
     * @return список {@link PropertyDiff}, по одному на каждый сравниваемый ключ.
     */
    private List<PropertyDiff> diffProperties(Map<String, String> left, Map<String, String> right,
                                               Set<String> excludeKeys) {
        Set<String> allKeys = new TreeSet<>(left.keySet());
        allKeys.addAll(right.keySet());
        allKeys.removeAll(excludeKeys);

        return allKeys.stream().map(key -> {
            String leftVal = left.get(key);
            String rightVal = right.get(key);
            if (leftVal == null) return new PropertyDiff(key, null, rightVal, DiffType.ADDED);
            if (rightVal == null) return new PropertyDiff(key, leftVal, null, DiffType.REMOVED);
            if (!leftVal.equals(rightVal)) return new PropertyDiff(key, leftVal, rightVal, DiffType.MODIFIED);
            return new PropertyDiff(key, leftVal, rightVal, DiffType.SAME);
        }).toList();
    }
}
