package org.example.controller;

import org.example.model.*;
import org.example.service.ComparisonService;
import org.example.service.ExcludeKeysService;
import org.example.service.GitService;
import org.example.service.YamlService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * REST-контроллер приложения Config Comparator.
 *
 * <p>Все эндпоинты доступны по базовому пути {@code /api}.
 * Запросы и ответы сериализуются в JSON.</p>
 *
 * <h2>Группы эндпоинтов</h2>
 * <ul>
 *   <li><b>Репозиторий</b> ({@code /api/repo/*}) — подключение, сброс, обновление веток.</li>
 *   <li><b>Навигация</b> ({@code /api/branches}, {@code /api/systems}, {@code /api/microservices}) —
 *       получение доступных веток, систем и микросервисов.</li>
 *   <li><b>Ключи</b> ({@code /api/keys}, {@code /api/exclude-keys}) —
 *       список всех параметров и управление постоянными исключениями.</li>
 *   <li><b>Сравнение</b> ({@code /api/compare}, {@code /api/compare/services}) —
 *       сравнение между контурами и между микросервисами.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api")
public class ApiController {

    private final GitService gitService;
    private final ComparisonService comparisonService;
    private final ExcludeKeysService excludeKeysService;
    private final YamlService yamlService;

    public ApiController(GitService gitService, ComparisonService comparisonService,
                         ExcludeKeysService excludeKeysService, YamlService yamlService) {
        this.gitService = gitService;
        this.comparisonService = comparisonService;
        this.excludeKeysService = excludeKeysService;
        this.yamlService = yamlService;
    }

    /**
     * Подключается к репозиторию согласно переданным настройкам.
     *
     * @param settings параметры подключения (URL или локальный путь, опциональный токен).
     * @return {@code {"status": "connected"}} при успехе или {@code {"error": "..."}} при ошибке.
     */
    @PostMapping("/repo/configure")
    public ResponseEntity<Map<String, String>> configure(@RequestBody RepoSettings settings) {
        try {
            gitService.configure(settings);
            return ResponseEntity.ok(Map.of("status", "connected"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Возвращает текущий статус подключения к репозиторию.
     *
     * @return {@code {"configured": true/false, "localRepo": true/false}}.
     */
    @GetMapping("/repo/status")
    public Map<String, Object> status() {
        return Map.of("configured", gitService.isConfigured(), "localRepo", gitService.isLocalRepo());
    }

    /**
     * Сбрасывает подключение к репозиторию.
     *
     * <p>Вызывается браузером при каждой загрузке страницы, чтобы предотвратить
     * использование устаревшего пути из предыдущей сессии.</p>
     *
     * @return {@code {"status": "disconnected"}}.
     */
    @PostMapping("/repo/reset")
    public ResponseEntity<Map<String, String>> reset() {
        gitService.reset();
        return ResponseEntity.ok(Map.of("status", "disconnected"));
    }

    /**
     * Выполняет {@code git fetch} для обновления удалённых веток.
     *
     * @return {@code {"status": "fetched"}} при успехе или {@code {"error": "..."}} при ошибке.
     */
    @PostMapping("/repo/fetch")
    public ResponseEntity<Map<String, String>> fetch() {
        try {
            gitService.fetchAll();
            return ResponseEntity.ok(Map.of("status", "fetched"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Возвращает список доступных веток репозитория.
     *
     * @return JSON-массив строк с именами веток.
     */
    @GetMapping("/branches")
    public ResponseEntity<?> getBranches() {
        try {
            return ResponseEntity.ok(gitService.getBranches());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Возвращает список систем (директорий верхнего уровня) в указанной ветке.
     *
     * @param branch имя ветки.
     * @return JSON-массив строк с именами систем.
     */
    @GetMapping("/systems")
    public ResponseEntity<?> getSystems(@RequestParam String branch) {
        try {
            return ResponseEntity.ok(comparisonService.getSystems(branch));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Возвращает объединённый список всех YAML-ключей системы в указанной ветке.
     *
     * <p>Используется для формирования списка исключаемых ключей в UI.</p>
     *
     * @param branch имя ветки.
     * @param system имя системы.
     * @return JSON-массив строк в нотации с точками.
     */
    @GetMapping("/keys")
    public ResponseEntity<?> getKeys(@RequestParam String branch, @RequestParam String system) {
        try {
            return ResponseEntity.ok(comparisonService.getAllKeys(branch, system));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Возвращает список микросервисов системы в указанной ветке.
     *
     * @param branch имя ветки.
     * @param system имя системы.
     * @return JSON-массив строк с именами микросервисов.
     */
    @GetMapping("/microservices")
    public ResponseEntity<?> getMicroservices(@RequestParam String branch, @RequestParam String system) {
        try {
            return ResponseEntity.ok(comparisonService.getMicroservices(branch, system));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Возвращает список постоянных исключаемых ключей, хранящихся на сервере.
     *
     * @return JSON-массив строк.
     */
    @GetMapping("/exclude-keys")
    public ResponseEntity<?> getExcludeKeys() {
        try {
            return ResponseEntity.ok(excludeKeysService.load());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Сохраняет список постоянных исключаемых ключей на сервере.
     *
     * <p>Полностью заменяет предыдущий список.</p>
     *
     * @param keys новый список ключей.
     * @return {@code {"status": "saved"}} при успехе или {@code {"error": "..."}} при ошибке.
     */
    @PostMapping("/exclude-keys")
    public ResponseEntity<?> saveExcludeKeys(@RequestBody List<String> keys) {
        try {
            excludeKeysService.save(keys);
            return ResponseEntity.ok(Map.of("status", "saved"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Редактирует значение одного параметра в YAML-файле на диске.
     *
     * <p>Доступно только для локального репозитория. Форматирование файла сохраняется:
     * изменяется только строка с целевым ключом.</p>
     *
     * @param request содержит относительный путь к файлу, ключ и новое значение.
     * @return {@code {"status": "saved"}} при успехе или {@code {"error": "..."}} при ошибке.
     */
    @PostMapping("/file/edit")
    public ResponseEntity<?> editFile(@RequestBody EditRequest request) {
        try {
            Path filePath = gitService.resolveLocalFilePath(request.filePath());
            String content = Files.readString(filePath, StandardCharsets.UTF_8);
            String patched = yamlService.patchKey(content, request.key(), request.newValue());
            if (patched.equals(content)) {
                return ResponseEntity.badRequest().body(Map.of("error",
                        "Ключ «" + request.key() + "» не найден в файле на диске. " +
                        "Убедитесь, что нужная ветка выгружена в рабочую директорию."));
            }
            Files.writeString(filePath, patched, StandardCharsets.UTF_8);
            return ResponseEntity.ok(Map.of("status", "saved"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Сравнивает два контура и возвращает дерево различий.
     *
     * @param request содержит левый/правый контур и список исключаемых ключей.
     * @return {@link CompareResponse} с полным деревом различий.
     */
    @PostMapping("/compare")
    public ResponseEntity<?> compare(@RequestBody CompareRequest request) {
        try {
            Set<String> exclude = request.excludeKeys() != null
                    ? new java.util.HashSet<>(request.excludeKeys()) : Set.of();
            return ResponseEntity.ok(comparisonService.compare(request.left(), request.right(), exclude));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Сравнивает несколько микросервисов внутри одной ветки.
     *
     * @param request содержит ветку, систему и список микросервисов для сравнения.
     * @return {@link MultiCompareResponse} с таблицами различий по файлам.
     */
    @PostMapping("/compare/services")
    public ResponseEntity<?> compareServices(@RequestBody ServiceCompareRequest request) {
        try {
            return ResponseEntity.ok(comparisonService.compareServices(request.branch(), request.system(), request.microservices()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
