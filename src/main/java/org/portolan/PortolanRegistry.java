package org.portolan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Registry utilities for Portolan catalog exports. */
public final class PortolanRegistry {
  public static final String DEFAULT_REGISTRY_URL =
      "https://raw.githubusercontent.com/portolan-sdi/portolan-registry/"
          + "refs/heads/main/exports/catalogs.json";

  private PortolanRegistry() {}

  @FunctionalInterface
  public interface JsonFetcher {
    ObjectNode fetch(String url);
  }

  public static List<RegistryCatalogEntry> loadRegistryEntries(
      String registryUrl,
      JsonFetcher fetcher,
      Set<String> catalogIds,
      boolean includeStale,
      Integer limit) {
    if (limit != null && limit <= 0) {
      return List.of();
    }
    JsonFetcher fetch = fetcher != null ? fetcher : url -> JsonSupport.readObject(URI.create(url));
    String effectiveRegistryUrl = registryUrl != null ? registryUrl : DEFAULT_REGISTRY_URL;
    ObjectNode registry = fetch.fetch(effectiveRegistryUrl);
    List<RegistryCatalogEntry> entries = new ArrayList<>();
    JsonNode links = registry.get("links");
    if (links == null || !links.isArray()) {
      return entries;
    }
    for (JsonNode link : links) {
      if (!link.isObject() || !"child".equals(JsonSupport.text(link, "rel"))) {
        continue;
      }
      String href = JsonSupport.text(link, "href");
      String registryId = JsonSupport.text(link, "portolan_registry:id");
      if (href == null || registryId == null) {
        continue;
      }
      String status = JsonSupport.text(link, "portolan_registry:status");
      if (!"valid".equals(status) && !includeStale) {
        continue;
      }
      if (catalogIds != null && !catalogIds.contains(registryId)) {
        continue;
      }
      String resolvedHref = HrefResolver.resolve(URI.create(effectiveRegistryUrl), href).toString();
      if (!isRemoteUrl(resolvedHref)) {
        continue;
      }
      JsonNode licenses = link.get("portolan_registry:licenses");
      JsonNode logo = link.get("portolan_registry:logo");
      entries.add(
          new RegistryCatalogEntry(
              registryId,
              resolvedHref,
              JsonSupport.text(link, "title"),
              status,
              horizontalBbox(link.get("bbox")),
              licenseIds(licenses),
              optionalLong(link.get("portolan_registry:collection_count")),
              optionalLong(link.get("portolan_registry:feature_count")),
              optionalLong(link.get("portolan_registry:total_size_bytes")),
              optionalText(link.get("portolan_registry:updated")),
              logo != null && logo.isObject() ? optionalText(logo.get("href")) : null,
              optionalText(link.get("portolan_registry:failure_reason"))));
      if (limit != null && entries.size() >= limit) {
        break;
      }
    }
    return List.copyOf(entries);
  }

  private static boolean isRemoteUrl(String value) {
    URI uri = URI.create(value);
    String scheme = normalized(uri.getScheme());
    return ("http".equals(scheme) || "https".equals(scheme))
        && uri.getRawAuthority() != null
        && !uri.getRawAuthority().isBlank();
  }

  private static List<Double> horizontalBbox(JsonNode value) {
    if (value == null || !value.isArray() || value.size() < 4 || value.size() % 2 != 0) {
      return null;
    }
    List<Double> coordinates = new ArrayList<>(value.size());
    for (JsonNode coordinate : value) {
      if (!coordinate.isNumber() || !Double.isFinite(coordinate.doubleValue())) {
        return null;
      }
      coordinates.add(coordinate.doubleValue());
    }
    int half = coordinates.size() / 2;
    return List.of(
        coordinates.get(0),
        coordinates.get(1),
        coordinates.get(half),
        coordinates.get(half + 1));
  }

  private static List<String> licenseIds(JsonNode value) {
    if (value == null || !value.isObject()) {
      return List.of();
    }
    List<String> licenses = new ArrayList<>();
    value.fieldNames().forEachRemaining(licenses::add);
    licenses.sort(Comparator.naturalOrder());
    return List.copyOf(licenses);
  }

  private static Long optionalLong(JsonNode value) {
    return value != null && value.isIntegralNumber() && value.canConvertToLong()
        ? value.longValue()
        : null;
  }

  private static String optionalText(JsonNode value) {
    return value != null && value.isTextual() && !value.textValue().isEmpty()
        ? value.textValue()
        : null;
  }

  public static Path downloadRegistryCatalog(String catalogUrl, Path outputDir, JsonFetcher fetcher) {
    return downloadRegistryCatalog(catalogUrl, outputDir, null, fetcher);
  }

  public static Path downloadRegistryCatalog(
      String catalogUrl, Path outputDir, String expectedCatalogId, JsonFetcher fetcher) {
    JsonFetcher fetch = fetcher != null ? fetcher : url -> JsonSupport.readObject(URI.create(url));
    ObjectNode catalog = fetch.fetch(catalogUrl);
    String catalogId = JsonSupport.text(catalog, "id");
    if (catalogId == null || catalogId.isBlank()) {
      catalogId = fallbackCatalogId(catalogUrl);
    }
    validateCatalogId(catalogId);
    if (expectedCatalogId != null) {
      validateCatalogId(expectedCatalogId);
      if (!catalogId.equals(expectedCatalogId)) {
        throw new IllegalArgumentException(
            "Catalog id '"
                + catalogId
                + "' does not match registry id '"
                + expectedCatalogId
                + "'");
      }
    }
    Path catalogRoot = outputDir.resolve(catalogId);
    Path lock = null;
    Path stagingRoot = null;
    try {
      Files.createDirectories(outputDir);
      lock = acquireCatalogLock(outputDir, catalogId);
      validateCatalogRoot(outputDir, catalogRoot);
      stagingRoot = Files.createTempDirectory(outputDir.toRealPath(), "." + catalogId + ".staging-");
      writeCatalogTree(
          URI.create(catalogUrl),
          catalog,
          URI.create(catalogUrl),
          stagingRoot,
          fetch,
          new HashSet<>(),
          new HashMap<>());
      publishSnapshot(stagingRoot, catalogRoot, outputDir);
      stagingRoot = null;
    } catch (IOException e) {
      throw new IllegalArgumentException("Cannot publish catalog snapshot " + catalogId, e);
    } finally {
      deleteTree(stagingRoot);
      if (lock != null) {
        try {
          Files.deleteIfExists(lock);
        } catch (IOException ignored) {
          // The completed operation is more important than a stale advisory lock.
        }
      }
    }
    return catalogRoot;
  }

  private static void writeCatalogTree(
      URI documentUri,
      ObjectNode document,
      URI rootUri,
      Path outputRoot,
      JsonFetcher fetch,
      Set<URI> visited,
      Map<Path, URI> targets) {
    URI normalizedDocumentUri = documentUri.normalize();
    visited.add(normalizedDocumentUri);
    Path relativePath = relativeDocumentPath(rootUri, documentUri);
    Path target = targetDocumentPath(outputRoot, relativePath, documentUri);
    URI owner = targets.putIfAbsent(target, normalizedDocumentUri);
    if (owner != null && !owner.equals(normalizedDocumentUri)) {
      throw new IllegalArgumentException(
        "Registry documents map to the same local path: " + owner + ", " + documentUri);
    }
    ObjectNode toWrite =
        "Collection".equals(JsonSupport.text(document, "type"))
            ? withAbsoluteAssetHrefs(documentUri, document)
            : document.deepCopy();

    JsonNode links = toWrite.get("links");
    if (links != null && links.isArray()) {
      for (JsonNode link : links) {
        if (!link.isObject()) {
          continue;
        }
        String href = JsonSupport.text(link, "href");
        if (href == null) {
          continue;
        }
        String rel = JsonSupport.text(link, "rel");
        URI linkedUri = HrefResolver.resolve(documentUri, href);
        ObjectNode linkObject = (ObjectNode) link;
        linkObject.put("href", linkedUri.toString());
        if ("root".equals(rel) || "parent".equals(rel)) {
          Path linkedTarget = localTarget(targets, linkedUri.normalize());
          if (linkedTarget != null) {
            linkObject.put("href", relativeLocalHref(target, linkedTarget));
          }
        }
        if (!"child".equals(rel)) {
          continue;
        }
        URI childUri = linkedUri;
        URI normalizedChildUri = childUri.normalize();
        Path childTarget =
            targetDocumentPath(outputRoot, relativeDocumentPath(rootUri, childUri), childUri);
        URI childOwner = targets.get(childTarget);
        if (childOwner != null && !childOwner.equals(normalizedChildUri)) {
          throw new IllegalArgumentException(
              "Registry documents map to the same local path: " + childOwner + ", " + childUri);
        }
        if (visited.contains(normalizedChildUri)) {
          if (normalizedChildUri.equals(childOwner)) {
            linkObject.put("href", relativeLocalHref(target, childTarget));
          }
          continue;
        }
        ObjectNode child = fetch.fetch(childUri.toString());
        String type = JsonSupport.text(child, "type");
        if ("Catalog".equals(type) || "Collection".equals(type)) {
          writeCatalogTree(childUri, child, rootUri, outputRoot, fetch, visited, targets);
          linkObject.put("href", relativeLocalHref(target, childTarget));
        }
      }
    }
    JsonSupport.writeObject(target, toWrite);
  }

  private static String relativeLocalHref(Path parentTarget, Path childTarget) {
    return parentTarget.getParent().relativize(childTarget).toString().replace('\\', '/');
  }

  private static Path localTarget(Map<Path, URI> targets, URI uri) {
    return targets.entrySet().stream()
        .filter(entry -> entry.getValue().equals(uri))
        .map(Map.Entry::getKey)
        .findFirst()
        .orElse(null);
  }

  private static ObjectNode withAbsoluteAssetHrefs(URI documentUri, ObjectNode collection) {
    ObjectNode updated = collection.deepCopy();
    JsonNode assets = updated.get("assets");
    if (assets == null || !assets.isObject()) {
      return updated;
    }
    assets
        .fields()
        .forEachRemaining(
            entry -> {
              JsonNode asset = entry.getValue();
              if (!asset.isObject()) {
                return;
              }
              String href = JsonSupport.text(asset, "href");
              if (href != null) {
                ((ObjectNode) asset)
                    .put("href", HrefResolver.resolve(documentUri, href).toString());
              }
            });
    return updated;
  }

  private static Path relativeDocumentPath(URI rootUri, URI documentUri) {
    if (!sameOrigin(rootUri, documentUri)) {
      throw new IllegalArgumentException("Child document has a different origin: " + documentUri);
    }
    Path rootParent = Path.of(rootUri.getRawPath()).getParent();
    Path documentPath = Path.of(documentUri.getRawPath());
    if (rootParent == null) {
      return Path.of(documentPath.getFileName().toString());
    }
    Path relative = rootParent.relativize(documentPath);
    if (relative.startsWith("..")) {
      throw new IllegalArgumentException("Child document is outside catalog root: " + documentUri);
    }
    return relative;
  }

  private static boolean sameOrigin(URI first, URI second) {
    return normalized(first.getScheme()).equals(normalized(second.getScheme()))
        && normalized(first.getAuthority()).equals(normalized(second.getAuthority()));
  }

  private static String normalized(String value) {
    return value == null ? "" : value.toLowerCase(java.util.Locale.ROOT);
  }

  private static Path targetDocumentPath(Path outputRoot, Path relativePath, URI documentUri) {
    Path resolvedRoot = outputRoot.toAbsolutePath().normalize();
    Path target = outputRoot.resolve(relativePath).toAbsolutePath().normalize();
    if (!target.startsWith(resolvedRoot)) {
      throw new IllegalArgumentException("Child document escapes catalog root: " + documentUri);
    }
    return target;
  }

  private static void validateCatalogId(String catalogId) {
    Path id = Path.of(catalogId);
    if (catalogId.isBlank()
        || ".".equals(catalogId)
        || "..".equals(catalogId)
        || catalogId.contains("\\")
        || id.getNameCount() != 1
        || !id.getFileName().toString().equals(catalogId)) {
      throw new IllegalArgumentException("Catalog id must be a safe directory name: " + catalogId);
    }
  }

  private static Path acquireCatalogLock(Path outputDir, String catalogId) throws IOException {
    Path lock = outputDir.toRealPath().resolve("." + catalogId + ".lock");
    try {
      return Files.createFile(lock);
    } catch (FileAlreadyExistsException e) {
      throw new IllegalStateException("Catalog download is already in progress: " + catalogId, e);
    }
  }

  private static void validateCatalogRoot(Path outputDir, Path catalogRoot) throws IOException {
    if (Files.isSymbolicLink(catalogRoot)) {
      throw new IllegalArgumentException("Catalog directory must not be a symlink: " + catalogRoot);
    }
    Path resolvedOutput = outputDir.toRealPath();
    Path resolvedCatalog = catalogRoot.toAbsolutePath().normalize();
    if (!resolvedCatalog.startsWith(resolvedOutput)) {
      throw new IllegalArgumentException("Catalog directory escapes output directory: " + catalogRoot);
    }
  }

  private static void publishSnapshot(Path stagingRoot, Path catalogRoot, Path outputDir)
      throws IOException {
    validateCatalogRoot(outputDir, catalogRoot);
    Path backup =
        Files.createTempDirectory(
            outputDir.toRealPath(), "." + catalogRoot.getFileName() + ".backup-");
    Files.delete(backup);
    boolean hadPrevious = Files.exists(catalogRoot, LinkOption.NOFOLLOW_LINKS);
    if (hadPrevious) {
      move(catalogRoot, backup);
    }
    try {
      move(stagingRoot, catalogRoot);
    } catch (IOException e) {
      if (hadPrevious) {
        move(backup, catalogRoot);
      }
      throw e;
    }
    deleteTree(backup);
  }

  private static void move(Path source, Path target) throws IOException {
    try {
      Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException e) {
      Files.move(source, target);
    }
  }

  private static void deleteTree(Path root) {
    if (root == null || Files.notExists(root, LinkOption.NOFOLLOW_LINKS)) {
      return;
    }
    try (var paths = Files.walk(root)) {
      paths.sorted(java.util.Comparator.reverseOrder()).forEach(PortolanRegistry::deleteQuietly);
    } catch (IOException ignored) {
      // Best-effort cleanup must not replace the original download failure.
    }
  }

  private static void deleteQuietly(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException ignored) {
      // Best-effort cleanup must not replace the original download failure.
    }
  }

  private static String fallbackCatalogId(String catalogUrl) {
    Path parent = Path.of(URI.create(catalogUrl).getPath()).getParent();
    if (parent == null || parent.getFileName() == null) {
      return "catalog";
    }
    return parent.getFileName().toString();
  }
}
