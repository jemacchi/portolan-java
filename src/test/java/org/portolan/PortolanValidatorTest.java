package org.portolan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PortolanValidatorTest {
  @TempDir Path tempDir;

  @Test
  void acceptsAValidCatalogWithoutOptionalLinksOrAssets() throws IOException {
    writeJson(
        tempDir.resolve("catalog.json"),
        """
        {
          "type": "Catalog",
          "stac_version": "1.1.0",
          "id": "demo",
          "description": "Demo catalog"
        }
        """);

    ValidationResult result = PortolanValidator.validate(PortolanCatalog.open(tempDir));

    assertTrue(result.valid());
    assertTrue(result.errors().isEmpty());
  }

  @Test
  void reportsEveryRequiredCatalogFieldAndLinkField() throws IOException {
    writeJson(
        tempDir.resolve("catalog.json"),
        """
        {
          "type": 7,
          "stac_version": " ",
          "description": null,
          "links": [{}, {"rel": "self"}, {"href": "./catalog.json"}]
        }
        """);

    ValidationResult result = PortolanValidator.validate(PortolanCatalog.open(tempDir));

    assertFalse(result.valid());
    assertEquals(
        List.of(
            "catalog.type",
            "catalog.stac_version",
            "catalog.id",
            "catalog.description",
            "catalog.links[0].rel",
            "catalog.links[0].href",
            "catalog.links[1].href",
            "catalog.links[2].rel"),
        result.errors().stream().map(ValidationError::path).toList());
    assertTrue(result.errors().stream().allMatch(error -> error.code().equals("PTL-STAC-001")
        || error.code().equals("PTL-STAC-002")));
    assertEquals("type is required", result.errors().get(0).message());
  }

  @Test
  void reportsRequiredCollectionAndItemContent() throws IOException {
    writeJson(
        tempDir.resolve("catalog.json"),
        """
        {
          "type": "Catalog",
          "stac_version": "1.1.0",
          "id": "demo",
          "description": "Demo",
          "links": [{"rel": "child", "href": "./collection.json"}]
        }
        """);
    writeJson(
        tempDir.resolve("collection.json"),
        """
        {
          "type": "Collection",
          "stac_version": 1,
          "id": " ",
          "links": [{"rel": "item", "href": "./item.json"}]
        }
        """);
    writeJson(
        tempDir.resolve("item.json"),
        """
        {
          "type": "Feature",
          "stac_version": " ",
          "id": "item-1",
          "properties": {},
          "assets": {}
        }
        """);

    ValidationResult result = PortolanValidator.validate(PortolanCatalog.open(tempDir));

    assertEquals(
        List.of(
            "collection. .stac_version",
            "collection. .id",
            "collection. .description",
            "collection. .license",
            "collection. .extent",
            "item.item-1.stac_version",
            "item.item-1.collection"),
        result.errors().stream().map(ValidationError::path).toList());
  }

  private static void writeJson(Path path, String json) throws IOException {
    Files.createDirectories(path.getParent());
    Files.writeString(path, json);
  }
}
