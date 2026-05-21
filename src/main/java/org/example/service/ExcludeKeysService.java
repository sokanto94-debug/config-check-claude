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

    private static final Path EXCLUDE_FILE = Path.of("exclude-keys.json");
    private static final Path INTRA_FILE   = Path.of("intra-keys.json");
    private final ObjectMapper mapper = new ObjectMapper();

    /** Загружает постоянные исключаемые ключи (вкладка «Сравнение контуров»). */
    public List<String> load() throws IOException {
        return loadFile(EXCLUDE_FILE);
    }

    /** Сохраняет постоянные исключаемые ключи. */
    public void save(List<String> keys) throws IOException {
        mapper.writeValue(EXCLUDE_FILE.toFile(), keys);
    }

    /** Загружает запомненный фильтр ключей (вкладка «Сравнение сервисов»). */
    public List<String> loadIntra() throws IOException {
        return loadFile(INTRA_FILE);
    }

    /** Сохраняет фильтр ключей для сравнения сервисов. */
    public void saveIntra(List<String> keys) throws IOException {
        mapper.writeValue(INTRA_FILE.toFile(), keys);
    }

    private List<String> loadFile(Path file) throws IOException {
        if (!Files.exists(file)) return new ArrayList<>();
        return mapper.readValue(file.toFile(), new TypeReference<List<String>>() {});
    }
}
