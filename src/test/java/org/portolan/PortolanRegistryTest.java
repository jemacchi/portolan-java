package org.portolan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PortolanRegistryTest {
  @TempDir Path tempDir;

  @Test
  void loadsRegistryEntriesWithFilters() {
    String registry =
        """
        {
          "links": [
            {
              "rel": "child",
              "href": "https://example.test/a/catalog.json",
              "title": "A",
              "portolan_registry:id": "catalog-a",
              "portolan_registry:status": "valid"
            },
            {
              "rel": "child",
              "href": "https://example.test/b/catalog.json",
              "portolan_registry:id": "catalog-b",
              "portolan_registry:status": "stale"
            },
            {"rel": "item", "href": "https://example.test/ignored/item.json"}
          ]
        }
        """;

    List<RegistryCatalogEntry> entries =
        PortolanRegistry.loadRegistryEntries(
            "https://registry.test/catalogs.json",
            url -> JsonSupport.readObject(registry),
            null,
            false,
            null);

    assertEquals(List.of("catalog-a"), entries.stream().map(RegistryCatalogEntry::id).toList());
    assertEquals("https://example.test/a/catalog.json", entries.get(0).url());
    assertEquals("A", entries.get(0).title());
  }

  @Test
  void loadsRegistryEntriesWithStaleFilterLimitAndInvalidShapes() {
    String registry =
        """
        {
          "links": [
            "not an object",
            {"rel": "item", "href": "https://example.test/ignored/item.json"},
            {"rel": "child", "href": "https://example.test/missing-id/catalog.json"},
            {"rel": "child", "href": 5, "portolan_registry:id": "bad-href"},
            {
              "rel": "child",
              "href": "https://example.test/b/catalog.json",
              "portolan_registry:id": "catalog-b",
              "portolan_registry:status": "stale"
            },
            {
              "rel": "child",
              "href": "https://example.test/c/catalog.json",
              "portolan_registry:id": "catalog-c",
              "portolan_registry:status": "valid"
            }
          ]
        }
        """;

    List<RegistryCatalogEntry> hidden =
        PortolanRegistry.loadRegistryEntries(
            "https://registry.test/catalogs.json",
            url -> JsonSupport.readObject(registry),
            null,
            false,
            null);
    List<RegistryCatalogEntry> included =
        PortolanRegistry.loadRegistryEntries(
            "https://registry.test/catalogs.json",
            url -> JsonSupport.readObject(registry),
            java.util.Set.of("catalog-b", "catalog-c"),
            true,
            1);

    assertEquals(List.of("catalog-c"), hidden.stream().map(RegistryCatalogEntry::id).toList());
    assertEquals(List.of("catalog-b"), included.stream().map(RegistryCatalogEntry::id).toList());
  }

  @Test
  void returnsNoRegistryEntriesWhenLinksAreMissing() {
    List<RegistryCatalogEntry> entries =
        PortolanRegistry.loadRegistryEntries(
            "https://registry.test/catalogs.json",
            url -> JsonSupport.readObject("{}"),
            null,
            false,
            null);

    assertTrue(entries.isEmpty());
  }

  @Test
  void resolvesRelativeRegistryCatalogUrls() {
    List<RegistryCatalogEntry> entries =
        PortolanRegistry.loadRegistryEntries(
            "https://registry.test/exports/catalogs.json",
            url ->
                JsonSupport.readObject(
                    """
                    {"links":[{
                      "rel":"child",
                      "href":"../demo/catalog.json",
                      "portolan_registry:id":"demo",
                      "portolan_registry:status":"valid"
                    }]}
                    """),
            null,
            false,
            null);

    assertEquals("https://registry.test/demo/catalog.json", entries.get(0).url());
  }

  @Test
  void usesDefaultRegistryUrlAndAppliesCatalogIdAndZeroLimitFilters() {
    AtomicReference<String> requestedUrl = new AtomicReference<>();
    String registry =
        """
        {
          "links": [
            {
              "rel": "child",
              "href": "https://example.test/a/catalog.json",
              "portolan_registry:id": "catalog-a",
              "portolan_registry:status": "valid"
            }
          ]
        }
        """;

    List<RegistryCatalogEntry> excluded =
        PortolanRegistry.loadRegistryEntries(
            null,
            url -> {
              requestedUrl.set(url);
              return JsonSupport.readObject(registry);
            },
            Set.of("another-catalog"),
            false,
            null);
    List<RegistryCatalogEntry> limited =
        PortolanRegistry.loadRegistryEntries(
            "memory:registry", url -> JsonSupport.readObject(registry), null, false, 0);
    List<RegistryCatalogEntry> belowLimit =
        PortolanRegistry.loadRegistryEntries(
            "memory:registry", url -> JsonSupport.readObject(registry), null, false, 2);

    assertEquals(PortolanRegistry.DEFAULT_REGISTRY_URL, requestedUrl.get());
    assertTrue(excluded.isEmpty());
    assertTrue(limited.isEmpty());
    assertEquals(1, belowLimit.size());
  }

  @Test
  void usesDefaultFileFetcherAndRejectsNonArrayRegistryLinks() throws Exception {
    Path validRegistry = tempDir.resolve("registry.json");
    Files.writeString(
        validRegistry,
        """
        {
          "links": [{
            "rel": "child",
            "href": "https://example.test/a/catalog.json",
            "portolan_registry:id": "catalog-a",
            "portolan_registry:status": "valid"
          }]
        }
        """);
    Path invalidRegistry = tempDir.resolve("invalid-registry.json");
    Files.writeString(invalidRegistry, "{\"links\":{}}");

    assertEquals(
        List.of("catalog-a"),
        PortolanRegistry.loadRegistryEntries(
                validRegistry.toUri().toString(), null, null, false, null)
            .stream()
            .map(RegistryCatalogEntry::id)
            .toList());
    assertTrue(
        PortolanRegistry.loadRegistryEntries(
                invalidRegistry.toUri().toString(), null, null, false, null)
            .isEmpty());
  }

  @Test
  void downloadsRegistryCatalogSnapshotWithAbsoluteAssetHrefs() throws Exception {
    Map<String, String> responses =
        Map.of(
            "https://example.test/demo/catalog.json",
            """
            {
              "type": "Catalog",
              "id": "demo",
              "links": [{"rel": "child", "href": "./roads/collection.json"}]
            }
            """,
            "https://example.test/demo/roads/collection.json",
            """
            {
              "type": "Collection",
              "stac_version": "1.1.0",
              "id": "roads",
              "description": "Roads",
              "license": "CC-BY-4.0",
              "extent": {"spatial": {"bbox": [[-71, -35, -70, -34]]}, "temporal": {"interval": [[null, null]]}},
              "links": [],
              "assets": {
                "data": {
                  "href": "./roads.parquet",
                  "type": "application/vnd.apache.parquet",
                  "roles": ["data"]
                }
              }
            }
            """);

    Path catalogRoot =
        PortolanRegistry.downloadRegistryCatalog(
            "https://example.test/demo/catalog.json",
            tempDir,
            url -> JsonSupport.readObject(responses.get(url)));

    assertEquals(tempDir.resolve("demo"), catalogRoot);
    assertTrue(Files.exists(catalogRoot.resolve("catalog.json")));
    String collection = Files.readString(catalogRoot.resolve("roads/collection.json"));
    assertTrue(collection.contains("https://example.test/demo/roads/roads.parquet"));
  }

  @Test
  void rewritesDownloadedChildrenToLocalHrefs() {
    String rootUrl = "https://example.test/demo/catalog.json";
    String childUrl = "https://example.test/demo/roads/collection.json";
    String itemUrl = "https://example.test/demo/roads/item.json";
    Map<String, String> responses =
        Map.of(
            rootUrl,
            """
            {
              "type": "Catalog",
              "id": "demo",
              "links": [
                {"rel": "self", "href": "https://example.test/demo/catalog.json"},
                {"rel": "child", "href": "https://example.test/demo/roads/collection.json"},
                {"rel": "child", "href": "https://example.test/demo/roads/item.json"}
              ]
            }
            """,
            childUrl,
            """
            {
              "type": "Collection",
              "id": "roads",
              "links": [
                {"rel": "child", "href": "https://example.test/demo/catalog.json"}
              ],
              "assets": {}
            }
            """,
            itemUrl,
            """
            {"type": "Feature", "id": "road-1"}
            """);

    Path catalogRoot =
        PortolanRegistry.downloadRegistryCatalog(
            rootUrl, tempDir, url -> JsonSupport.readObject(responses.get(url)));

    var catalog = JsonSupport.readObject(catalogRoot.resolve("catalog.json").toUri());
    var collection =
        JsonSupport.readObject(catalogRoot.resolve("roads/collection.json").toUri());
    assertEquals(rootUrl, catalog.path("links").get(0).path("href").asText());
    assertEquals("roads/collection.json", catalog.path("links").get(1).path("href").asText());
    assertEquals(itemUrl, catalog.path("links").get(2).path("href").asText());
    assertEquals("../catalog.json", collection.path("links").get(0).path("href").asText());
  }

  @Test
  void makesNonDownloadedRelativeLinksAbsoluteAndKeepsRootAndParentLocal() {
    String rootUrl = "https://example.test/demo/catalog.json";
    String collectionUrl = "https://example.test/demo/roads/collection.json";
    String featureUrl = "https://example.test/demo/roads/features/road-1.json";
    Map<String, String> responses =
        Map.of(
            rootUrl,
            """
            {"type":"Catalog","id":"demo","links":[
              {"rel":"child","href":"./roads/collection.json"}
            ]}
            """,
            collectionUrl,
            """
            {"type":"Collection","id":"roads","links":[
              {"rel":"root","href":"https://example.test/demo/catalog.json"},
              {"rel":"parent","href":"https://example.test/demo/catalog.json"},
              {"rel":"item","href":"./items/road-1.json"},
              {"rel":"child","href":"./features/road-1.json"}
            ],"assets":{}}
            """,
            featureUrl,
            """
            {"type":"Feature","id":"road-1"}
            """);

    Path catalogRoot =
        PortolanRegistry.downloadRegistryCatalog(
            rootUrl, tempDir, url -> JsonSupport.readObject(responses.get(url)));

    var collection =
        JsonSupport.readObject(catalogRoot.resolve("roads/collection.json").toUri());
    assertEquals("../catalog.json", collection.path("links").get(0).path("href").asText());
    assertEquals("../catalog.json", collection.path("links").get(1).path("href").asText());
    assertEquals(
        "https://example.test/demo/roads/items/road-1.json",
        collection.path("links").get(2).path("href").asText());
    assertEquals(featureUrl, collection.path("links").get(3).path("href").asText());
  }

  @Test
  void resolvesRegistryDocumentAndAssetHrefsWithSpaces() {
    String rootUrl = "https://example.test/demo/catalog.json";
    String collectionUrl =
        "https://example.test/demo/road%20data/collection.json?version=one%20value#section%20one";
    Map<String, String> responses =
        Map.of(
            rootUrl,
            """
            {"type":"Catalog","id":"demo","links":[
              {
                "rel":"child",
                "href":"./road data/collection.json?version=one value#section one"
              }
            ]}
            """,
            collectionUrl,
            """
            {"type":"Collection","id":"roads","links":[],"assets":{
              "data": {
                "href":"./Linee impianto a fune.parquet?download=full map#sheet one"
              },
              "encoded": {"href":"./already%20encoded.parquet"}
            }}
            """);

    Path catalogRoot =
        PortolanRegistry.downloadRegistryCatalog(
            rootUrl, tempDir, url -> JsonSupport.readObject(responses.get(url)));

    var catalog = JsonSupport.readObject(catalogRoot.resolve("catalog.json").toUri());
    var collection =
        JsonSupport.readObject(
            catalogRoot.resolve("road%20data/collection.json").toUri());
    assertEquals(
        "road%20data/collection.json", catalog.path("links").get(0).path("href").asText());
    assertEquals(
        "https://example.test/demo/road%20data/Linee%20impianto%20a%20fune.parquet?download=full%20map#sheet%20one",
        collection.path("assets").path("data").path("href").asText());
    assertEquals(
        "https://example.test/demo/road%20data/already%20encoded.parquet",
        collection.path("assets").path("encoded").path("href").asText());
  }

  @Test
  void downloadsNestedRegistryCatalogWithFallbackId() throws Exception {
    Map<String, String> responses =
        Map.of(
            "https://example.test/nested/catalog.json",
            """
            {
              "type": "Catalog",
              "links": [{"rel": "child", "href": "./theme/catalog.json"}]
            }
            """,
            "https://example.test/nested/theme/catalog.json",
            """
            {
              "type": "Catalog",
              "id": "theme",
              "links": [{"rel": "child", "href": "./roads/collection.json"}]
            }
            """,
            "https://example.test/nested/theme/roads/collection.json",
            """
            {
              "type": "Collection",
              "id": "roads",
              "links": [],
              "assets": {
                "data": {"href": "../roads.parquet"}
              }
            }
            """);

    Path catalogRoot =
        PortolanRegistry.downloadRegistryCatalog(
            "https://example.test/nested/catalog.json",
            tempDir,
            url -> JsonSupport.readObject(responses.get(url)));

    assertEquals(tempDir.resolve("nested"), catalogRoot);
    assertTrue(Files.exists(catalogRoot.resolve("catalog.json")));
    assertTrue(Files.exists(catalogRoot.resolve("theme/catalog.json")));
    String collection = Files.readString(catalogRoot.resolve("theme/roads/collection.json"));
    assertTrue(collection.contains("https://example.test/nested/theme/roads.parquet"));
  }

  @Test
  void ignoresInvalidChildrenAndPreservesInvalidAssetShapes() throws Exception {
    Map<String, String> responses =
        Map.of(
            "https://example.test/demo/catalog.json",
            """
            {
              "type": "Catalog",
              "id": "demo",
              "links": [
                "not an object",
                {"rel": "child", "href": 5},
                {"rel": "child", "href": "./ignored/item.json"},
                {"rel": "child", "href": "./roads/collection.json"}
              ]
            }
            """,
            "https://example.test/demo/ignored/item.json",
            """
            {"type": "Feature", "id": "ignored", "links": []}
            """,
            "https://example.test/demo/roads/collection.json",
            """
            {
              "type": "Collection",
              "id": "roads",
              "links": [],
              "assets": {
                "bad": "not an object",
                "missing_href": {"type": "application/vnd.apache.parquet"},
                "data": {"href": "./roads.parquet"}
              }
            }
            """);

    Path catalogRoot =
        PortolanRegistry.downloadRegistryCatalog(
            "https://example.test/demo/catalog.json",
            tempDir,
            url -> JsonSupport.readObject(responses.get(url)));

    assertTrue(Files.notExists(catalogRoot.resolve("ignored/item.json")));
    String collection = Files.readString(catalogRoot.resolve("roads/collection.json"));
    assertTrue(collection.contains("\"bad\" : \"not an object\""));
    assertTrue(collection.contains("\"href\" : \"https://example.test/demo/roads/roads.parquet\""));
  }

  @Test
  void preservesCollectionsWithoutAssetObjects() throws Exception {
    Map<String, String> responses =
        Map.of(
            "https://example.test/demo/catalog.json",
            """
            {
              "type": "Catalog",
              "id": "demo",
              "links": [{"rel": "child", "href": "./roads/collection.json"}]
            }
            """,
            "https://example.test/demo/roads/collection.json",
            """
            {
              "type": "Collection",
              "id": "roads",
              "links": [],
              "assets": []
            }
            """);

    Path catalogRoot =
        PortolanRegistry.downloadRegistryCatalog(
            "https://example.test/demo/catalog.json",
            tempDir,
            url -> JsonSupport.readObject(responses.get(url)));

    String collection = Files.readString(catalogRoot.resolve("roads/collection.json"));
    assertTrue(collection.contains("\"assets\" : [ ]"));
  }

  @Test
  void downloadsWithDefaultFileFetcherAndFallsBackToCatalogId() throws Exception {
    Path source = tempDir.resolve("source/catalog.json");
    Files.createDirectories(source.getParent());
    Files.writeString(source, "{\"type\":\"Catalog\",\"id\":\"demo\"}");

    Path catalogRoot =
        PortolanRegistry.downloadRegistryCatalog(
            source.toUri().toString(), tempDir.resolve("downloads"), null);
    Path fallbackRoot =
        PortolanRegistry.downloadRegistryCatalog(
            "https://example.test/catalog.json",
            tempDir.resolve("fallback"),
            url -> JsonSupport.readObject("{\"type\":\"Catalog\",\"id\":\" \"}"));

    assertTrue(Files.exists(catalogRoot.resolve("catalog.json")));
    assertEquals(tempDir.resolve("fallback/catalog"), fallbackRoot);
    assertTrue(Files.exists(fallbackRoot.resolve("catalog.json")));
  }

  @Test
  void downloadsEachDocumentOnceWhenCatalogLinksFormACycle() throws Exception {
    AtomicInteger fetchCount = new AtomicInteger();
    Map<String, String> responses =
        Map.of(
            "https://example.test/demo/catalog.json",
            """
            {
              "type": "Catalog",
              "id": "demo",
              "links": [{"rel": "child", "href": "./nested/catalog.json"}]
            }
            """,
            "https://example.test/demo/nested/catalog.json",
            """
            {
              "type": "Catalog",
              "id": "nested",
              "links": [{"rel": "child", "href": "../catalog.json"}]
            }
            """);

    Path catalogRoot =
        PortolanRegistry.downloadRegistryCatalog(
            "https://example.test/demo/catalog.json",
            tempDir,
            url -> {
              fetchCount.incrementAndGet();
              return JsonSupport.readObject(responses.get(url));
            });

    assertTrue(Files.exists(catalogRoot.resolve("catalog.json")));
    assertTrue(Files.exists(catalogRoot.resolve("nested/catalog.json")));
    assertEquals(2, fetchCount.get());
  }

  @Test
  void rejectsChildrenOnAnotherOriginAndHandlesParentlessUris() throws Exception {
    Map<String, String> responses =
        Map.of(
            "root/catalog.json",
            """
            {
              "type": "Catalog",
              "links": [
                {"rel": "item", "href": "./ignored.json"},
                {"rel": "child", "href": "https://example.test/collection.json"}
              ]
            }
            """,
            "https://example.test/collection.json",
            "{\"type\":\"Collection\",\"id\":\"remote\"}");

    assertThrows(
        IllegalArgumentException.class,
        () ->
            PortolanRegistry.downloadRegistryCatalog(
                "root/catalog.json",
                tempDir,
                url -> JsonSupport.readObject(responses.get(url))));
    Path parentlessRoot =
        PortolanRegistry.downloadRegistryCatalog(
            "catalog.json",
            tempDir.resolve("parentless"),
            url -> JsonSupport.readObject("{\"type\":\"Catalog\"}"));

    assertEquals(tempDir.resolve("parentless/catalog"), parentlessRoot);
    assertTrue(Files.exists(parentlessRoot.resolve("catalog.json")));
  }

  @Test
  void rejectsUnsafeChildPathsBeforeFetchingThem() {
    AtomicInteger fetchCount = new AtomicInteger();

    assertThrows(
        IllegalArgumentException.class,
        () ->
            PortolanRegistry.downloadRegistryCatalog(
                "https://example.test/demo/catalog.json",
                tempDir,
                url -> {
                  fetchCount.incrementAndGet();
                  return JsonSupport.readObject(
                      """
                      {"type":"Catalog","id":"demo","links":[
                        {"rel":"child","href":"../../outside.json"}
                      ]}
                      """);
                }));

    assertEquals(1, fetchCount.get());
  }

  @Test
  void rejectsCatalogRootSymlinks() throws Exception {
    Path outside = Files.createDirectory(tempDir.resolve("outside"));
    Files.createSymbolicLink(tempDir.resolve("demo"), outside);

    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                PortolanRegistry.downloadRegistryCatalog(
                    "https://example.test/demo/catalog.json",
                    tempDir,
                    url -> JsonSupport.readObject("{\"type\":\"Catalog\",\"id\":\"demo\"}")));

    assertTrue(error.getMessage().contains("must not be a symlink"));
    try (var files = Files.list(outside)) {
      assertTrue(files.findAny().isEmpty());
    }
  }

  @Test
  void rejectsRegistryIdMismatch() {
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                PortolanRegistry.downloadRegistryCatalog(
                    "https://example.test/demo/catalog.json",
                    tempDir,
                    "selected",
                    url -> JsonSupport.readObject("{\"type\":\"Catalog\",\"id\":\"other\"}")));

    assertTrue(error.getMessage().contains("does not match registry id"));
  }

  @Test
  void rejectsUnsafeCatalogIds() {
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                PortolanRegistry.downloadRegistryCatalog(
                    "https://example.test/demo/catalog.json",
                    tempDir,
                    url -> JsonSupport.readObject("{\"type\":\"Catalog\",\"id\":\"../other\"}")));

    assertTrue(error.getMessage().contains("safe directory name"));
  }

  @Test
  void rejectsConcurrentDownloadsForTheSameCatalog() throws Exception {
    Files.createFile(tempDir.resolve(".demo.lock"));

    IllegalStateException error =
        assertThrows(
            IllegalStateException.class,
            () ->
                PortolanRegistry.downloadRegistryCatalog(
                    "https://example.test/demo/catalog.json",
                    tempDir,
                    url -> JsonSupport.readObject("{\"type\":\"Catalog\",\"id\":\"demo\"}")));

    assertTrue(error.getMessage().contains("already in progress"));
  }

  @Test
  void replacesACompletePreviousSnapshot() throws Exception {
    Path root = Files.createDirectory(tempDir.resolve("demo"));
    Files.writeString(root.resolve("stale.json"), "stale");

    Path downloaded =
        PortolanRegistry.downloadRegistryCatalog(
            "https://example.test/demo/catalog.json",
            tempDir,
            url -> JsonSupport.readObject("{\"type\":\"Catalog\",\"id\":\"demo\"}"));

    assertTrue(Files.exists(downloaded.resolve("catalog.json")));
    assertTrue(Files.notExists(downloaded.resolve("stale.json")));
  }

  @Test
  void preservesPreviousSnapshotWhenChildFetchFails() throws Exception {
    Path root = Files.createDirectory(tempDir.resolve("demo"));
    Path previous = root.resolve("catalog.json");
    Files.writeString(previous, "previous");

    assertThrows(
        IllegalStateException.class,
        () ->
            PortolanRegistry.downloadRegistryCatalog(
                "https://example.test/demo/catalog.json",
                tempDir,
                url -> {
                  if (url.endsWith("catalog.json")) {
                    return JsonSupport.readObject(
                        """
                        {"type":"Catalog","id":"demo","links":[
                          {"rel":"child","href":"./missing.json"}
                        ]}
                        """);
                  }
                  throw new IllegalStateException("unavailable");
                }));

    assertEquals("previous", Files.readString(previous));
    try (var files = Files.list(tempDir)) {
      assertTrue(files.noneMatch(path -> path.getFileName().toString().startsWith(".demo.staging-")));
    }
  }

  @Test
  void rejectsDocumentsThatMapToTheSamePath() {
    AtomicInteger fetchCount = new AtomicInteger();

    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                PortolanRegistry.downloadRegistryCatalog(
                    "https://example.test/demo/catalog.json",
                    tempDir,
                    url -> {
                      fetchCount.incrementAndGet();
                      if (url.endsWith("catalog.json")) {
                        return JsonSupport.readObject(
                            """
                            {"type":"Catalog","id":"demo","links":[
                              {"rel":"child","href":"./collection.json?v=1"},
                              {"rel":"child","href":"./collection.json?v=2"}
                            ]}
                            """);
                      }
                      return JsonSupport.readObject("{\"type\":\"Collection\",\"id\":\"roads\"}");
                    }));

    assertTrue(error.getMessage().contains("same local path"));
    assertEquals(2, fetchCount.get());
  }

  @Test
  void preservesCatalogsWhoseLinksHaveTheWrongShape() throws Exception {
    Path root =
        PortolanRegistry.downloadRegistryCatalog(
            "https://example.test/demo/catalog.json",
            tempDir,
            url -> JsonSupport.readObject("{\"type\":\"Catalog\",\"id\":\"demo\",\"links\":{}}"));

    assertTrue(Files.exists(root.resolve("catalog.json")));
  }
}
