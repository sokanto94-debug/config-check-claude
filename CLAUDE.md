# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Run the application (port 8081)
mvn spring-boot:run

# Build a runnable JAR
mvn package

# Compile only
mvn compile
```

There are no tests in this project.

## Architecture

**Config Comparator** is a Spring Boot 3.3.5 / Java 21 web app that compares YAML configuration files across git branches. The UI is a single-page app served from `src/main/resources/static/index.html` (Russian language).

### Data flow

1. **GitService** clones a remote repo (or opens a local one) using JGit with `setNoCheckout(true)` — files are never written to disk. All file reads go through JGit's `TreeWalk`/`ObjectLoader` directly against git objects. Remote repos support OAuth2 token auth via `UsernamePasswordCredentialsProvider("oauth2", token)`.

2. **ComparisonService** interprets the repo's directory structure as: `{system}/{microservice}/{filename}.yaml`. "Systems" are the top-level directories containing at least one two-level-deep YAML file. "Microservices" are the second-level directories. Only direct (non-nested) YAML files under `{system}/{microservice}/` are diffed — files in subdirectories are skipped by `extractDirectFiles`.

3. **YamlService** flattens nested YAML into dotted key paths (`a.b.c`) and list-indexed paths (`a.list[0]`). All values are coerced to strings for comparison.

4. **ApiController** exposes the REST API at `/api`. The comparison result tree is: `CompareResponse → ServiceDiff[] → FileDiff[] → PropertyDiff[]`, each with a `DiffType` (ADDED / REMOVED / MODIFIED / SAME).

### Key design constraints

- `GitService` is a singleton `@Service` holding a single `volatile Git` instance. Re-configuring replaces it; concurrent calls share it without locking (only `configure()` is `synchronized`).
- The temp directory for clones is created once at startup and cleaned up on reconfigure; the `@PreDestroy` hook closes the JGit handle.
- `getSystems` and `getMicroservices` re-walk the full file list on every call — no caching.
