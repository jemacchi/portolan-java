package org.portolan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
    JsonFetcher fetch = fetcher != null ? fetcher : url -> JsonSupport.readObject(URI.create(url));
    ObjectNode registry = fetch.fetch(registryUrl != null ? registryUrl : DEFAULT_REGISTRY_URL);
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
      entries.add(new RegistryCatalogEntry(registryId, href, JsonSupport.text(link, "title"), status));
      if (limit != null && entries.size() >= limit) {
        break;
      }
    }
    return List.copyOf(entries);
  }

  public static Path downloadRegistryCatalog(String catalogUrl, Path outputDir, JsonFetcher fetcher) {
    JsonFetcher fetch = fetcher != null ? fetcher : url -> JsonSupport.readObject(URI.create(url));
    ObjectNode catalog = fetch.fetch(catalogUrl);
    String catalogId = JsonSupport.text(catalog, "id");
    if (catalogId == null || catalogId.isBlank()) {
      catalogId = fallbackCatalogId(catalogUrl);
    }
    Path catalogRoot = outputDir.resolve(catalogId);
    writeCatalogTree(URI.create(catalogUrl), catalog, URI.create(catalogUrl), catalogRoot, fetch);
    return catalogRoot;
  }

  private static void writeCatalogTree(
      URI documentUri, ObjectNode document, URI rootUri, Path outputRoot, JsonFetcher fetch) {
    Path relativePath = relativeDocumentPath(rootUri, documentUri);
    Path target = outputRoot.resolve(relativePath);
    ObjectNode toWrite = document;
    if ("Collection".equals(JsonSupport.text(document, "type"))) {
      toWrite = withAbsoluteAssetHrefs(documentUri, document);
    }
    JsonSupport.writeObject(target, toWrite);

    JsonNode links = document.get("links");
    if (links == null || !links.isArray()) {
      return;
    }
    for (JsonNode link : links) {
      if (!link.isObject() || !"child".equals(JsonSupport.text(link, "rel"))) {
        continue;
      }
      String href = JsonSupport.text(link, "href");
      if (href == null) {
        continue;
      }
      URI childUri = documentUri.resolve(href);
      ObjectNode child = fetch.fetch(childUri.toString());
      String type = JsonSupport.text(child, "type");
      if ("Catalog".equals(type) || "Collection".equals(type)) {
        writeCatalogTree(childUri, child, rootUri, outputRoot, fetch);
      }
    }
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
                ((ObjectNode) asset).put("href", documentUri.resolve(href).toString());
              }
            });
    return updated;
  }

  private static Path relativeDocumentPath(URI rootUri, URI documentUri) {
    Path rootParent = Path.of(rootUri.getPath()).getParent();
    Path documentPath = Path.of(documentUri.getPath());
    if (rootParent == null) {
      return Path.of(documentPath.getFileName().toString());
    }
    try {
      return rootParent.relativize(documentPath);
    } catch (IllegalArgumentException e) {
      return Path.of(documentPath.getFileName().toString());
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
