package org.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Точка входа приложения Config Comparator.
 *
 * <p>Config Comparator — веб-приложение для сравнения YAML-конфигураций
 * между ветками git-репозитория. Поддерживает два режима:</p>
 * <ul>
 *   <li><b>Сравнение контуров</b> — сравнивает конфигурации одного микросервиса
 *       между двумя ветками (например, lt vs preprod).</li>
 *   <li><b>Сравнение микросервисов</b> — сравнивает конфигурации нескольких
 *       микросервисов внутри одной ветки.</li>
 * </ul>
 *
 * <p>Приложение запускается на порту {@code 8081} и отдаёт одностраничный
 * интерфейс из {@code src/main/resources/static/index.html}.</p>
 */
@SpringBootApplication
public class ConfigCompareApp {
    public static void main(String[] args) {
        SpringApplication.run(ConfigCompareApp.class, args);
    }
}
