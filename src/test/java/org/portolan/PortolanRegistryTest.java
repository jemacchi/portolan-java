package org.portolan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
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
}
