package org.portolan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

abstract class PortolanDocument {
  protected final ObjectNode data;
  protected final URI href;

  PortolanDocument(ObjectNode data, URI href) {
    this.data = data;
    this.href = href;
  }

  public String id() {
    return JsonSupport.text(data, "id");
  }

  public URI href() {
    return href;
  }

  public ObjectNode data() {
    return data.deepCopy();
  }

  public List<PortolanLink> links() {
    JsonNode links = data.get("links");
    List<PortolanLink> result = new ArrayList<>();
    if (links == null || !links.isArray()) {
      return result;
    }
    for (JsonNode link : links) {
      if (!link.isObject()) {
        continue;
      }
      String rel = JsonSupport.text(link, "rel");
      String rawHref = JsonSupport.text(link, "href");
      if (rel == null || rawHref == null) {
        continue;
      }
      result.add(
          new PortolanLink(
              rel,
              href.resolve(rawHref),
              JsonSupport.text(link, "type"),
              JsonSupport.text(link, "title"),
              ((ObjectNode) link).deepCopy()));
    }
    return result;
  }

  public List<PortolanAsset> assets() {
    JsonNode assets = data.get("assets");
    List<PortolanAsset> result = new ArrayList<>();
    if (assets == null || !assets.isObject()) {
      return result;
    }
    assets
        .fields()
        .forEachRemaining(
            entry -> {
              JsonNode asset = entry.getValue();
              if (!asset.isObject()) {
                return;
              }
              String rawHref = JsonSupport.text(asset, "href");
              if (rawHref == null) {
                return;
              }
              List<String> roles = new ArrayList<>();
              JsonNode roleNodes = asset.get("roles");
              if (roleNodes != null && roleNodes.isArray()) {
                for (JsonNode role : roleNodes) {
                  if (role.isTextual()) {
                    roles.add(role.asText());
                  }
                }
              }
              String mediaType = JsonSupport.text(asset, "type");
              if (mediaType == null) {
                mediaType = JsonSupport.text(asset, "media_type");
              }
              result.add(
                  new PortolanAsset(
                      entry.getKey(),
                      href.resolve(rawHref),
                      mediaType,
                      List.copyOf(roles),
                      JsonSupport.text(asset, "title"),
                      JsonSupport.text(asset, "description"),
                      ((ObjectNode) asset).deepCopy()));
            });
    return result;
  }
}
