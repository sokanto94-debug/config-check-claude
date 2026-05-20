package org.example.service;

import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Сервис для разбора и «уплощения» YAML-файлов.
 *
 * <p>Преобразует вложенную YAML-структуру в плоский {@code Map<String, String>},
 * где ключи записаны в нотации с точками, а индексы списков — в квадратных скобках:</p>
 * <pre>
 * server:
 *   port: 8080
 * items:
 *   - name: foo
 * ──────────────────────
 * "server.port"   → "8080"
 * "items[0].name" → "foo"
 * </pre>
 * <p>Все значения приводятся к строке через {@link String#valueOf(Object)};
 * {@code null}-значения в YAML становятся пустой строкой {@code ""}.</p>
 */
@Service
public class YamlService {

    /**
     * Разбирает YAML-строку и возвращает плоский словарь параметров.
     *
     * @param content содержимое YAML-файла; {@code null} или пустая строка
     *                возвращает пустой словарь.
     * @return неизменяемый или отсортированный {@link TreeMap} вида
     *         {@code dotted.key.path → stringValue}.
     */
    public Map<String, String> flatten(String content) {
        if (content == null || content.isBlank()) return Map.of();
        Object loaded = new Yaml().load(content);
        if (!(loaded instanceof Map<?, ?> map)) return Map.of();
        Map<String, String> result = new TreeMap<>();
        flatten("", map, result);
        return result;
    }

    /**
     * Рекурсивно обходит узел YAML-дерева, накапливая плоские записи в {@code result}.
     *
     * @param prefix текущий префикс ключа (пустая строка для корня).
     * @param value  текущий узел: {@link Map}, {@link List} или скалярное значение.
     * @param result целевой словарь для записи листовых значений.
     */
    @SuppressWarnings("unchecked")
    private void flatten(String prefix, Object value, Map<String, String> result) {
        if (value instanceof Map<?, ?> map) {
            for (var entry : ((Map<String, Object>) map).entrySet()) {
                String key = prefix.isEmpty() ? String.valueOf(entry.getKey()) : prefix + "." + entry.getKey();
                flatten(key, entry.getValue(), result);
            }
        } else if (value instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                flatten(prefix + "[" + i + "]", list.get(i), result);
            }
        } else {
            result.put(prefix, value == null ? "" : String.valueOf(value));
        }
    }
}
