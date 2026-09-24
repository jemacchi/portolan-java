package org.portolan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PortolanCatalogTest {
  @TempDir Path tempDir;

  @Test
  void opensCatalogAndTraversesCollectionsItemsAndAssets() throws Exception {
    writeJson(
        tempDir.resolve("catalog.json"),
        """
        {
          "type": "Catalog",
          "stac_version": "1.1.0",
          "id": "demo",
          "description": "Demo catalog",
          "links": [
            {"rel": "child", "href": "./roads/collection.json", "type": "application/json"}
          ]
        }
        """);
    writeJson(
        tempDir.resolve("roads/collection.json"),
        """
        {
          "type": "Collection",
          "stac_version": "1.1.0",
          "id": "roads",
          "description": "Roads",
          "license": "CC-BY-4.0",
          "extent": {
            "spatial": {"bbox": [[-71.0, -35.0, -70.0, -34.0]]},
            "temporal": {"interval": [[null, null]]}
          },
          "links": [{"rel": "item", "href": "./road-1/road-1.json"}],
          "assets": {
            "data": {
              "href": "./roads.parquet",
              "type": "application/vnd.apache.parquet",
              "roles": ["data"]
            }
          }
        }
        """);
    writeJson(
        tempDir.resolve("roads/road-1/road-1.json"),
        """
        {
          "type": "Feature",
          "stac_version": "1.1.0",
          "id": "road-1",
          "collection": "roads",
          "geometry": null,
          "bbox": [-71.0, -35.0, -70.0, -34.0],
          "properties": {"datetime": null},
          "links": [],
          "assets": {
            "data": {
              "href": "./road-1.parquet",
              "type": "application/vnd.apache.parquet",
              "roles": ["data"]
            }
          }
        }
        """);

    PortolanCatalog catalog = PortolanCatalog.open(tempDir);
    List<PortolanCollection> collections = catalog.collections();
    List<PortolanItem> items = collections.get(0).items();
    PortolanAsset collectionAsset = collections.get(0).assets().get(0);
    PortolanAsset itemAsset = items.get(0).assets().get(0);

    assertEquals("demo", catalog.id());
    assertEquals(List.of("roads"), collections.stream().map(PortolanCollection::id).toList());
    assertEquals(List.of("road-1"), items.stream().map(PortolanItem::id).toList());
    assertEquals(tempDir.resolve("roads/roads.parquet").toUri(), collectionAsset.href());
    assertEquals("application/vnd.apache.parquet", collectionAsset.mediaType());
    assertEquals(AssetFormat.GEOPARQUET, collectionAsset.format());
    assertEquals(List.of("data"), collectionAsset.roles());
    assertEquals(tempDir.resolve("roads/road-1/road-1.parquet").toUri(), itemAsset.href());
  }

  @Test
  void opensCatalogFromFileUriAndExposesDefensiveDataCopies() throws Exception {
    writeJson(
        tempDir.resolve("catalog.json"),
        """
        {
          "type": "Catalog",
          "stac_version": "1.1.0",
          "id": "demo",
          "description": "Demo catalog",
          "links": []
        }
        """);

    PortolanCatalog catalog = PortolanCatalog.open(tempDir.resolve("catalog.json").toUri());
    catalog.data().put("id", "changed");

    assertEquals("demo", catalog.id());
    assertEquals(tempDir.resolve("catalog.json").toUri(), catalog.href());
  }

  @Test
  void traversesNestedChildCatalogs() throws Exception {
    writeJson(
        tempDir.resolve("catalog.json"),
        """
        {
          "type": "Catalog",
          "stac_version": "1.1.0",
          "id": "root",
          "description": "Root catalog",
          "links": [{"rel": "child", "href": "./transport/catalog.json"}]
        }
        """);
    writeJson(
        tempDir.resolve("transport/catalog.json"),
        """
        {
          "type": "Catalog",
          "stac_version": "1.1.0",
          "id": "transport",
          "description": "Transport catalog",
          "links": [{"rel": "child", "href": "./roads/collection.json"}]
        }
        """);
    writeJson(
        tempDir.resolve("transport/roads/collection.json"),
        """
        {
          "type": "Collection",
          "stac_version": "1.1.0",
          "id": "roads",
          "description": "Roads",
          "license": "CC-BY-4.0",
          "extent": {"spatial": {"bbox": [[-71, -35, -70, -34]]}, "temporal": {"interval": [[null, null]]}},
          "links": []
        }
        """);

    PortolanCatalog catalog = PortolanCatalog.open(tempDir.resolve("catalog.json"));

    assertEquals(List.of("roads"), catalog.collections().stream().map(PortolanCollection::id).toList());
  }

  @Test
  void documentAccessorsIgnoreInvalidLinksAssetsAndItems() throws Exception {
    writeJson(tempDir.resolve("ignored.json"), "{\"type\":\"Catalog\",\"id\":\"ignored\",\"links\":[]}");
    writeJson(
        tempDir.resolve("item.json"),
        """
        {
          "type": "Feature",
          "id": "road-1",
          "collection": "roads",
          "properties": {},
          "links": [],
          "assets": {}
        }
        """);
    PortolanCollection collection =
        new PortolanCollection(
            JsonSupport.readObject(
                """
                {
                  "type": "Collection",
                  "id": "roads",
                  "links": [
                    "not an object",
                    {"rel": "self"},
                    {"href": "./missing-rel.json"},
                    {"rel": "root", "href": "./ignored.json"},
                    {"rel": "item", "href": "./ignored.json"},
                    {"rel": "item", "href": "./item.json", "type": "application/json", "title": "Item"}
                  ],
                  "assets": {
                    "bad": "not an object",
                    "missing_href": {"type": "application/vnd.apache.parquet"},
                    "media_type": {
                      "href": "./roads.parquet",
                      "media_type": "application/vnd.apache.parquet",
                      "roles": ["data", 5],
                      "title": "Roads",
                      "description": "Road network"
                    }
                  }
                }
                """),
            tempDir.resolve("collection.json").toUri());

    List<PortolanLink> links = collection.links();
    List<PortolanAsset> assets = collection.assets();

    assertEquals(List.of("root", "item", "item"), links.stream().map(PortolanLink::rel).toList());
    assertEquals(List.of("road-1"), collection.items().stream().map(PortolanItem::id).toList());
    assertEquals(1, assets.size());
    assertEquals("media_type", assets.get(0).key());
    assertEquals("application/vnd.apache.parquet", assets.get(0).mediaType());
    assertEquals(List.of("data"), assets.get(0).roles());
    assertEquals("Roads", assets.get(0).title());
    assertEquals("Road network", assets.get(0).description());
  }

  @Test
  void classifiesAssetsByMediaTypeAndHref() {
    URI document = URI.create("https://example.test/collection.json");

    assertEquals(
        AssetFormat.GEOPARQUET,
        new PortolanAsset(
                "parquet",
                URI.create("https://example.test/data"),
                "application/vnd.apache.parquet",
                List.of(),
                null,
                null,
                null)
            .format());
    assertEquals(
        AssetFormat.COG,
        new PortolanAsset("cog", document.resolve("image.tif"), null, List.of(), null, null, null)
            .format());
    assertEquals(
        AssetFormat.PMTILES,
        new PortolanAsset(
                "pmtiles",
                URI.create("https://example.test/tiles"),
                "application/vnd.pmtiles",
                List.of(),
                null,
                null,
                null)
            .format());
    assertEquals(
        AssetFormat.UNKNOWN,
        new PortolanAsset(
                "unknown",
                URI.create("https://example.test/readme.txt"),
                "text/plain",
                List.of(),
                null,
                null,
                null)
            .format());
    assertEquals(
        AssetFormat.UNKNOWN,
        new PortolanAsset("unknown", null, null, List.of(), null, null, null).format());
  }

  @Test
  void validatorReportsCatalogCollectionItemAndAssetErrors() throws Exception {
    writeJson(
        tempDir.resolve("catalog.json"),
        """
        {
          "type": "Catalog",
          "stac_version": "1.1.0",
          "id": "demo",
          "description": "Demo catalog",
          "links": [{"rel": "child", "href": "./roads/collection.json"}]
        }
        """);
    writeJson(
        tempDir.resolve("roads/collection.json"),
        """
        {
          "type": "Collection",
          "stac_version": "1.1.0",
          "id": "roads",
          "description": "Roads",
          "extent": {"spatial": {"bbox": [[-71, -35, -70, -34]]}, "temporal": {"interval": [[null, null]]}},
          "links": [{"rel": "item", "href": "./road-1/road-1.json"}],
          "assets": {"data": {"type": "application/vnd.apache.parquet"}}
        }
        """);
    writeJson(
        tempDir.resolve("roads/road-1/road-1.json"),
        """
        {
          "type": "Feature",
          "stac_version": "1.1.0",
          "id": "road-1",
          "properties": {"datetime": null},
          "links": [],
          "assets": {"data": {"href": "./road-1.parquet"}}
        }
        """);

    ValidationResult result = PortolanValidator.validate(PortolanCatalog.open(tempDir));

    assertFalse(result.valid());
    assertEquals(
        List.of(
            "collection.roads.license",
            "collection.roads.assets.data.href",
            "item.road-1.collection"),
        result.errors().stream().map(ValidationError::path).toList());
  }

  @Test
  void validatorReportsShapeErrorsForLinksAssetsExtentAndProperties() throws Exception {
    writeJson(
        tempDir.resolve("catalog.json"),
        """
        {
          "type": "Catalog",
          "stac_version": "1.1.0",
          "id": "demo",
          "description": "Demo catalog",
          "links": [{"rel": "child", "href": "./roads/collection.json"}]
        }
        """);
    writeJson(
        tempDir.resolve("roads/collection.json"),
        """
        {
          "type": "Collection",
          "stac_version": "1.1.0",
          "id": "roads",
          "description": "Roads",
          "license": "CC-BY-4.0",
          "extent": "not an object",
          "links": ["not an object", {"rel": "item", "href": "./road-1/road-1.json"}],
          "assets": ["not an object"]
        }
        """);
    writeJson(
        tempDir.resolve("roads/road-1/road-1.json"),
        """
        {
          "type": "Feature",
          "stac_version": "1.1.0",
          "id": "road-1",
          "collection": "roads",
          "properties": [],
          "links": "not an array",
          "assets": {"bad": "not an object"}
        }
        """);

    ValidationResult result = PortolanValidator.validate(PortolanCatalog.open(tempDir));

    assertFalse(result.valid());
    assertEquals(
        List.of(
            "collection.roads.extent",
            "collection.roads.links[0]",
            "collection.roads.assets",
            "item.road-1.properties",
            "item.road-1.links",
            "item.road-1.assets.bad"),
        result.errors().stream().map(ValidationError::path).toList());
  }

  @Test
  void jsonSupportRejectsInvalidAndNonObjectJson() {
    assertThrows(IllegalArgumentException.class, () -> JsonSupport.readObject("[]"));
    assertThrows(IllegalArgumentException.class, () -> JsonSupport.readObject("{"));
  }

  private static void writeJson(Path path, String json) throws Exception {
    Files.createDirectories(path.getParent());
    Files.writeString(path, json);
  }
}
