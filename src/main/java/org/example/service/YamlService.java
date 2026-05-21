package org.example.service;

import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayDeque;
import java.util.Deque;
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
     * Патчит одно скалярное значение в YAML-строке, сохраняя исходное форматирование.
     *
     * <p>Использует построчный сканер с отслеживанием отступов — комментарии,
     * пустые строки и незатронутые ключи остаются неизменными.</p>
     *
     * <p>Ограничения: не поддерживает ключи с индексами массивов ({@code items[0]}),
     * блочные скаляры ({@code |}, {@code >}) и flow-коллекции ({@code {…}}, {@code […]}).</p>
     *
     * @param content содержимое YAML-файла.
     * @param dotKey  путь к параметру в нотации с точками (например, {@code server.port}).
     * @param newValue новое значение.
     * @return изменённое содержимое файла; исходное — если ключ не найден или не поддерживается.
     */
    public String patchKey(String content, String dotKey, String newValue) {
        if (dotKey.contains("[")) return content; // массивы не поддерживаются

        String[] parts = dotKey.split("\\.");
        String[] lines = content.split("\n", -1);

        // Стек хранит {indent, depth} для уже сопоставлённых сегментов пути
        Deque<int[]> stack = new ArrayDeque<>();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String stripped = line.stripLeading();

            if (stripped.isEmpty() || stripped.startsWith("#")) continue;

            int colonIdx = stripped.indexOf(':');
            if (colonIdx < 0) continue;

            int indent = line.length() - stripped.length();
            String keyStr = stripped.substring(0, colonIdx).trim();
            String rest = stripped.substring(colonIdx + 1); // всё после ':'

            // Откатываем стек до текущего уровня отступа
            while (!stack.isEmpty() && stack.peek()[0] >= indent) {
                stack.pop();
            }

            int depth = stack.size();
            if (depth >= parts.length) continue;

            if (!keyStr.equals(parts[depth])) continue;

            if (depth == parts.length - 1) {
                // Нашли целевой ключ
                String trimmedRest = rest.trim();
                // Пропускаем блочные скаляры и flow-коллекции — их структура многострочная
                if (trimmedRest.equals("|") || trimmedRest.equals(">")
                        || trimmedRest.startsWith("{") || trimmedRest.startsWith("[")) {
                    return content;
                }
                if (trimmedRest.isEmpty()) {
                    // Значение null/пустое: добавляем новое значение после двоеточия
                    lines[i] = line.stripTrailing() + " " + newValue;
                    return String.join("\n", lines);
                }
                // Находим позицию начала значения в строке
                int valueStart = line.indexOf(':') + 1;
                while (valueStart < line.length() && line.charAt(valueStart) == ' ') {
                    valueStart++;
                }
                String formattedValue = preserveQuoting(trimmedRest, newValue);
                lines[i] = line.substring(0, valueStart) + formattedValue;
                return String.join("\n", lines);
            } else {
                // Промежуточный сегмент пути совпал
                stack.push(new int[]{indent, depth});
            }
        }

        return content; // ключ не найден
    }

    /**
     * Форматирует новое значение, сохраняя стиль кавычек оригинала.
     */
    private String preserveQuoting(String original, String newValue) {
        if (original.length() >= 2) {
            char f = original.charAt(0), l = original.charAt(original.length() - 1);
            if (f == '"' && l == '"') return "\"" + newValue.replace("\"", "\\\"") + "\"";
            if (f == '\'' && l == '\'') return "'" + newValue.replace("'", "''") + "'";
        }
        return newValue;
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
