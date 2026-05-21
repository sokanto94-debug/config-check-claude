package org.example.service;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.example.model.ScanRequest;
import org.example.model.ServiceScanResult;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Сервис сканирования папки с компонентами.
 *
 * <p>Обходит поддиректории указанной папки, находит git-репозитории (наличие {@code .git}),
 * проверяет наличие заданной ветки и собирает коммит-сообщения, уникальные для этой ветки
 * (т.е. не входящие в историю базовой ветки).</p>
 */
@Service
public class ScanService {

    /**
     * Сканирует папку и возвращает результаты по каждому сервису.
     *
     * @param folderPath  путь к папке с компонентами.
     * @param branch      имя ветки для поиска.
     * @param baseBranch  базовая ветка (может быть {@code null} — тогда автоопределение).
     * @return список результатов, отсортированный по имени сервиса.
     * @throws IOException если папка недоступна.
     */
    public List<ServiceScanResult> scan(ScanRequest request) throws IOException {
        Path root = Path.of(request.folderPath());
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Путь не является директорией: " + request.folderPath());
        }

        List<ServiceScanResult> results = new ArrayList<>();

        try (Stream<Path> stream = Files.list(root)) {
            List<Path> gitDirs = stream
                    .filter(Files::isDirectory)
                    .filter(p -> Files.exists(p.resolve(".git")))
                    .sorted()
                    .toList();

            for (Path dir : gitDirs) {
                String name = dir.getFileName().toString();
                try {
                    results.add(scanOne(dir, name, request.branch(), request.baseBranch(), request.fetch()));
                } catch (Exception e) {
                    results.add(new ServiceScanResult(name, false, List.of(), e.getMessage()));
                }
            }
        }

        return results;
    }

    private ServiceScanResult scanOne(Path repoPath, String name, String branch, String baseBranch, boolean doFetch) {
        try (Git git = Git.open(repoPath.toFile())) {
            if (doFetch) {
                try {
                    git.fetch().setRemote("origin").setForceUpdate(true).call();
                } catch (Exception ignored) {
                    // fetch нефатален: сеть недоступна или нет remote — продолжаем с локальными данными
                }
            }

            Repository repo = git.getRepository();

            ObjectId branchTip = resolveRef(repo, branch);
            if (branchTip == null) {
                return new ServiceScanResult(name, false, List.of(), null);
            }

            ObjectId baseId = resolveBase(repo, branchTip, baseBranch);
            List<String> commits = collectCommits(repo, branchTip, baseId);
            return new ServiceScanResult(name, true, commits, null);
        } catch (Exception e) {
            return new ServiceScanResult(name, false, List.of(), e.getMessage());
        }
    }

    /**
     * Находит базовую ветку репозитория.
     *
     * <p>Если {@code explicit} задан — используется он напрямую.</p>
     *
     * <p>Иначе перебирает все ветки репозитория и выбирает ту, чей merge-base
     * с {@code branchTip} самый новый. Ветки, чья верхушка уже является предком
     * {@code branchTip} (т.е. полностью вмержены), пропускаются — это позволяет
     * корректно определять базу даже когда в ветку вмержено множество фич.</p>
     */
    private ObjectId resolveBase(Repository repo, ObjectId branchTip, String explicit) throws IOException {
        if (explicit != null && !explicit.isBlank()) {
            for (String ref : List.of(
                    "refs/heads/" + explicit,
                    "refs/remotes/origin/" + explicit,
                    explicit)) {
                ObjectId id = repo.resolve(ref);
                if (id != null && !id.equals(branchTip)) return id;
            }
            return null;
        }

        // Автоопределение: ищем ветку с самым свежим merge-base,
        // пропуская уже вмержённые (чья верхушка — предок нашей ветки)
        ObjectId bestCandidate = null;
        int bestTime = -1;

        for (Ref ref : repo.getRefDatabase().getRefs()) {
            String name = ref.getName();
            if (!name.startsWith("refs/heads/") && !name.startsWith("refs/remotes/")) continue;

            ObjectId otherTip = ref.getObjectId();
            if (otherTip == null || otherTip.equals(branchTip)) continue;

            ObjectId mb = findMergeBase(repo, branchTip, otherTip);
            if (mb == null) continue;

            // Верхушка другой ветки является предком нашей → она уже вмержена, пропускаем
            if (mb.equals(otherTip)) continue;

            try (RevWalk walk = new RevWalk(repo)) {
                int commitTime = walk.parseCommit(mb).getCommitTime();
                if (commitTime > bestTime) {
                    bestTime = commitTime;
                    bestCandidate = otherTip;
                }
            }
        }

        return bestCandidate;
    }

    /**
     * Собирает коммиты, начиная от вершины ветки до точки ответвления от базовой ветки.
     * Использует merge-base для точного определения точки ветвления.
     */
    private List<String> collectCommits(Repository repo, ObjectId branchTip, ObjectId baseId) throws IOException {
        try (RevWalk walk = new RevWalk(repo)) {
            walk.markStart(walk.parseCommit(branchTip));

            if (baseId != null) {
                // Находим merge-base для точного ограничения истории
                ObjectId mergeBase = findMergeBase(repo, branchTip, baseId);
                if (mergeBase != null) {
                    walk.markUninteresting(walk.parseCommit(mergeBase));
                } else {
                    walk.markUninteresting(walk.parseCommit(baseId));
                }
            }

            List<String> messages = new ArrayList<>();
            for (RevCommit commit : walk) {
                messages.add(commit.getFullMessage().strip());
            }
            return messages;
        }
    }

    /**
     * Вычисляет merge-base двух коммитов (общий предок).
     */
    private ObjectId findMergeBase(Repository repo, ObjectId a, ObjectId b) throws IOException {
        try (RevWalk walk = new RevWalk(repo)) {
            walk.setRevFilter(RevFilter.MERGE_BASE);
            walk.markStart(walk.parseCommit(a));
            walk.markStart(walk.parseCommit(b));
            RevCommit base = walk.next();
            return base != null ? base.getId() : null;
        }
    }

    private ObjectId resolveRef(Repository repo, String branch) throws IOException {
        for (String ref : List.of(
                "refs/heads/" + branch,
                "refs/remotes/origin/" + branch,
                branch)) {
            ObjectId id = repo.resolve(ref);
            if (id != null) return id;
        }
        return null;
    }
}
