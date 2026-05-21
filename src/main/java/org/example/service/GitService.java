package org.example.service;

import jakarta.annotation.PreDestroy;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.example.model.RepoSettings;

import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Сервис для работы с git-репозиторием через JGit.
 *
 * <p>Хранит единственный экземпляр {@link Git}, который инициализируется
 * методом {@link #configure(RepoSettings)} и заменяется при повторном вызове.
 * Поддерживаются два режима подключения:</p>
 * <ul>
 *   <li><b>Локальный</b> — открывает существующий клон по пути {@code localPath}.</li>
 *   <li><b>Удалённый</b> — клонирует репозиторий по {@code url} с флагом
 *       {@code setNoCheckout(true)}: файлы на диск не извлекаются, все чтения
 *       идут напрямую через git-объекты.</li>
 * </ul>
 *
 * <p>Для авторизации в приватных репозиториях используется OAuth2-токен,
 * передаваемый как пароль пользователя {@code oauth2}.</p>
 *
 * <p>Временная директория для клонов создаётся один раз при старте приложения
 * и очищается при повторном вызове {@link #configure(RepoSettings)}.</p>
 *
 * <p><b>Потокобезопасность:</b> метод {@link #configure(RepoSettings)} и {@link #reset()}
 * синхронизированы. Остальные методы используют {@code volatile}-поле {@code git}
 * без блокировки — пригодно для сценария «один пишет, несколько читают».</p>
 */
@Service
public class GitService {

    private volatile Git git;
    private volatile RepoSettings settings;
    private final Path tempDir;

    /**
     * Создаёт сервис и инициализирует временную директорию для клонов.
     *
     * @throws IOException если не удаётся создать временную директорию.
     */
    public GitService() throws IOException {
        tempDir = Files.createTempDirectory("config-compare-");
    }

    /**
     * Подключается к репозиторию согласно переданным настройкам.
     *
     * <p>Закрывает предыдущее подключение (если было), затем либо открывает
     * локальный репозиторий, либо клонирует удалённый во временную директорию.</p>
     *
     * @param settings параметры подключения.
     * @throws IllegalArgumentException если не указан ни URL, ни локальный путь.
     * @throws Exception                при ошибке JGit (сетевая ошибка, неверный токен и т.п.).
     */
    public synchronized void configure(RepoSettings settings) throws Exception {
        if (git != null) {
            git.close();
            git = null;
        }
        this.settings = settings;

        boolean hasLocal = settings.localPath() != null && !settings.localPath().isBlank();
        boolean hasUrl = settings.url() != null && !settings.url().isBlank();

        if (hasLocal) {
            git = Git.open(new File(settings.localPath()));
        } else if (hasUrl) {
            Path cloneDir = tempDir.resolve("repo");
            if (Files.exists(cloneDir)) {
                try (Stream<Path> walk = Files.walk(cloneDir)) {
                    walk.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
                }
            }
            var clone = Git.cloneRepository()
                    .setURI(settings.url())
                    .setDirectory(cloneDir.toFile())
                    .setCloneAllBranches(true)
                    .setNoCheckout(true);
            if (settings.token() != null && !settings.token().isBlank()) {
                clone.setCredentialsProvider(
                        new UsernamePasswordCredentialsProvider("oauth2", settings.token()));
            }
            git = clone.call();
        } else {
            throw new IllegalArgumentException("Укажите URL или локальный путь");
        }
    }

    /**
     * Выполняет {@code git fetch --all} для обновления удалённых веток.
     *
     * @throws Exception если репозиторий не настроен или возникла сетевая ошибка.
     */
    public void fetchAll() throws Exception {
        requireConfigured();
        var fetch = git.fetch()
                .setRemote("origin")
                .setForceUpdate(true);
        if (settings != null && settings.token() != null && !settings.token().isBlank()) {
            fetch.setCredentialsProvider(
                    new UsernamePasswordCredentialsProvider("oauth2", settings.token()));
        }
        fetch.call();
    }

    /**
     * Возвращает объединённый список имён веток: удалённые + локальные, без дубликатов.
     *
     * <p>Имена очищаются от префиксов {@code refs/remotes/origin/} и {@code refs/heads/}.
     * Ветка {@code HEAD} из удалённых исключается.</p>
     *
     * @return отсортированный список имён веток.
     * @throws GitAPIException если репозиторий не настроен или JGit вернул ошибку.
     */
    public List<String> getBranches() throws GitAPIException {
        requireConfigured();
        List<String> remote = git.branchList()
                .setListMode(ListBranchCommand.ListMode.REMOTE)
                .call()
                .stream()
                .map(r -> r.getName().replaceFirst("^refs/remotes/[^/]+/", ""))
                .filter(n -> !n.equals("HEAD"))
                .toList();

        List<String> local = git.branchList().call()
                .stream()
                .map(r -> r.getName().replaceFirst("^refs/heads/", ""))
                .toList();

        return Stream.concat(remote.stream(), local.stream())
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * Возвращает список путей всех YAML-файлов в указанной ветке.
     *
     * <p>Чтение выполняется через JGit {@link TreeWalk} прямо из git-объектов,
     * без извлечения файлов на диск. Включаются файлы с расширениями
     * {@code .yaml} и {@code .yml}.</p>
     *
     * @param branch имя ветки (без префикса {@code refs/}).
     * @return список относительных путей файлов внутри репозитория.
     * @throws IOException          при ошибке чтения git-объектов.
     * @throws RuntimeException     если ветка не найдена.
     */
    public List<String> listYamlFiles(String branch) throws IOException {
        requireConfigured();
        Repository repo = git.getRepository();
        ObjectId objectId = resolveRef(repo, branch);
        if (objectId == null) throw new RuntimeException("Ветка не найдена: " + branch);

        try (RevWalk revWalk = new RevWalk(repo)) {
            RevCommit commit = revWalk.parseCommit(objectId);
            TreeWalk treeWalk = new TreeWalk(repo);
            treeWalk.addTree(commit.getTree());
            treeWalk.setRecursive(true);
            List<String> paths = new ArrayList<>();
            while (treeWalk.next()) {
                String path = treeWalk.getPathString();
                if (path.endsWith(".yaml") || path.endsWith(".yml")) {
                    paths.add(path);
                }
            }
            treeWalk.close();
            return paths;
        }
    }

    /**
     * Читает содержимое файла из указанной ветки.
     *
     * <p>Чтение выполняется через JGit {@link ObjectLoader} без извлечения на диск.</p>
     *
     * @param branch   имя ветки.
     * @param filePath путь к файлу внутри репозитория.
     * @return содержимое файла в кодировке UTF-8; {@code null}, если файл или ветка не найдены.
     * @throws IOException при ошибке чтения git-объектов.
     */
    public String readFile(String branch, String filePath) throws IOException {
        requireConfigured();
        Repository repo = git.getRepository();
        ObjectId objectId = resolveRef(repo, branch);
        if (objectId == null) return null;

        try (RevWalk revWalk = new RevWalk(repo)) {
            RevCommit commit = revWalk.parseCommit(objectId);
            TreeWalk treeWalk = TreeWalk.forPath(repo, filePath, commit.getTree());
            if (treeWalk == null) return null;
            ObjectLoader loader = repo.open(treeWalk.getObjectId(0));
            treeWalk.close();
            return new String(loader.getBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Возвращает {@code true}, если репозиторий настроен и готов к работе.
     */
    public boolean isConfigured() {
        return git != null;
    }

    /**
     * Возвращает {@code true}, если репозиторий открыт как локальный (не клонирован из URL).
     * Только для локальных репозиториев доступно редактирование файлов на диске.
     */
    public boolean isLocalRepo() {
        return settings != null && settings.localPath() != null && !settings.localPath().isBlank();
    }

    /**
     * Возвращает абсолютный путь к файлу на диске по его относительному пути в репозитории.
     *
     * @param relativeFilePath относительный путь (например, {@code 15_10/login/application.yaml}).
     * @return абсолютный {@link Path} к файлу.
     * @throws IllegalStateException если репозиторий не является локальным.
     */
    public java.nio.file.Path resolveLocalFilePath(String relativeFilePath) {
        if (!isLocalRepo()) {
            throw new IllegalStateException("Редактирование доступно только для локального репозитория");
        }
        return java.nio.file.Path.of(settings.localPath()).resolve(relativeFilePath);
    }

    /**
     * Сбрасывает подключение к репозиторию и очищает настройки.
     *
     * <p>Вызывается при загрузке страницы, чтобы предотвратить использование
     * устаревшего пути между сессиями.</p>
     */
    public synchronized void reset() {
        if (git != null) { git.close(); git = null; }
        settings = null;
    }

    /**
     * Разрешает имя ветки в {@link ObjectId}, проверяя последовательно:
     * удалённый ref, локальный ref, прямой ref.
     *
     * @param repo   репозиторий JGit.
     * @param branch имя ветки.
     * @return {@link ObjectId} или {@code null}, если ветка не найдена.
     * @throws IOException при ошибке доступа к репозиторию.
     */
    private ObjectId resolveRef(Repository repo, String branch) throws IOException {
        // Локальное репо: только локальные ветки. Удалённый клон: только remote tracking refs.
        List<String> candidates = isLocalRepo()
                ? List.of("refs/heads/" + branch, branch)
                : List.of("refs/remotes/origin/" + branch, branch);
        for (String ref : candidates) {
            ObjectId id = repo.resolve(ref);
            if (id != null) return id;
        }
        return null;
    }

    private void requireConfigured() {
        if (git == null) throw new IllegalStateException("Репозиторий не настроен");
    }

    /**
     * Закрывает JGit-дескриптор при остановке приложения.
     */
    @PreDestroy
    public void cleanup() {
        if (git != null) git.close();
    }
}
