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
}
