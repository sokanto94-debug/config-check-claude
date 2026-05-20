package org.example.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Сервис хранения постоянных исключаемых ключей.
 *
 * <p>Ключи сохраняются в JSON-файл {@code exclude-keys.json} в рабочей директории
 * приложения (рядом с JAR). Файл создаётся автоматически при первом сохранении.</p>
 *
 * <p>Постоянные ключи автоматически отмечаются как исключённые при каждом открытии
 * страницы сравнения, независимо от браузера и сессии.</p>
 */
@Service
public class ExcludeKeysService {

    private static final Path FILE = Path.of("exclude-keys.json");
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Загружает список постоянных исключаемых ключей из файла.
     *
     * @return список ключей; пустой список, если файл не существует.
     * @throws IOException если файл существует, но не может быть прочитан или разобран.
     */
    public List<String> load() throws IOException {
        if (!Files.exists(FILE)) return new ArrayList<>();
        return mapper.readValue(FILE.toFile(), new TypeReference<List<String>>() {});
    }

    /**
     * Сохраняет список постоянных исключаемых ключей в файл.
     *
     * <p>Полностью перезаписывает предыдущее содержимое файла.</p>
     *
     * @param keys список ключей для сохранения.
     * @throws IOException если файл не может быть записан.
     */
    public void save(List<String> keys) throws IOException {
        mapper.writeValue(FILE.toFile(), keys);
    }
}
