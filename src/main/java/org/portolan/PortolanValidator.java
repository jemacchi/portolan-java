package org.portolan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;

/** Validate loaded Portolan catalog structures. */
public final class PortolanValidator {
  private PortolanValidator() {}

  public static ValidationResult validate(PortolanCatalog catalog) {
    List<ValidationError> errors = new ArrayList<>();
    validateCatalog(catalog.data(), "catalog", errors);
    for (PortolanCollection collection : catalog.collections()) {
      validateCollection(collection.data(), "collection." + collection.id(), errors);
      for (PortolanItem item : collection.items()) {
        validateItem(item.data(), "item." + item.id(), errors);
      }
    }
    return new ValidationResult(List.copyOf(errors));
  }

  private static void validateCatalog(ObjectNode data, String path, List<ValidationError> errors) {
    requireString(data, "type", path, errors, "PTL-STAC-001");
    requireString(data, "stac_version", path, errors, "PTL-STAC-001");
    requireString(data, "id", path, errors, "PTL-STAC-001");
    requireString(data, "description", path, errors, "PTL-STAC-001");
    validateLinks(data, path, errors);
  }

  private static void validateCollection(
      ObjectNode data, String path, List<ValidationError> errors) {
    requireString(data, "type", path, errors, "PTL-STAC-001");
    requireString(data, "stac_version", path, errors, "PTL-STAC-001");
    requireString(data, "id", path, errors, "PTL-STAC-001");
    requireString(data, "description", path, errors, "PTL-STAC-001");
    requireString(data, "license", path, errors, "PTL-STAC-001");
    if (!data.path("extent").isObject()) {
      errors.add(new ValidationError("PTL-STAC-001", path + ".extent", "extent is required"));
    }
    validateLinks(data, path, errors);
    validateAssets(data, path, errors);
  }

  private static void validateItem(ObjectNode data, String path, List<ValidationError> errors) {
    requireString(data, "type", path, errors, "PTL-STAC-001");
    requireString(data, "stac_version", path, errors, "PTL-STAC-001");
    requireString(data, "id", path, errors, "PTL-STAC-001");
    requireString(data, "collection", path, errors, "PTL-STAC-001");
    if (!data.path("properties").isObject()) {
      errors.add(
          new ValidationError("PTL-STAC-001", path + ".properties", "properties is required"));
    }
    validateLinks(data, path, errors);
    validateAssets(data, path, errors);
  }

  private static void validateLinks(ObjectNode data, String path, List<ValidationError> errors) {
    JsonNode links = data.get("links");
    if (links == null) {
      return;
    }
    if (!links.isArray()) {
      errors.add(new ValidationError("PTL-STAC-002", path + ".links", "links must be an array"));
      return;
    }
    int index = 0;
    for (JsonNode link : links) {
      String linkPath = path + ".links[" + index + "]";
      if (!link.isObject()) {
        errors.add(new ValidationError("PTL-STAC-002", linkPath, "link must be an object"));
      } else {
        requireString((ObjectNode) link, "rel", linkPath, errors, "PTL-STAC-002");
        requireString((ObjectNode) link, "href", linkPath, errors, "PTL-STAC-002");
      }
      index++;
    }
  }

  private static void validateAssets(ObjectNode data, String path, List<ValidationError> errors) {
    JsonNode assets = data.get("assets");
    if (assets == null) {
      return;
    }
    if (!assets.isObject()) {
      errors.add(new ValidationError("PTL-STAC-003", path + ".assets", "assets must be an object"));
      return;
    }
    assets
        .fields()
        .forEachRemaining(
            entry -> {
              String assetPath = path + ".assets." + entry.getKey();
              JsonNode asset = entry.getValue();
              if (!asset.isObject()) {
                errors.add(new ValidationError("PTL-STAC-003", assetPath, "asset must be an object"));
                return;
              }
              requireString((ObjectNode) asset, "href", assetPath, errors, "PTL-STAC-003");
            });
  }

  private static void requireString(
      ObjectNode data, String field, String path, List<ValidationError> errors, String code) {
    JsonNode value = data.get(field);
    if (value == null || !value.isTextual() || value.asText().isBlank()) {
      errors.add(new ValidationError(code, path + "." + field, field + " is required"));
    }
  }
}
