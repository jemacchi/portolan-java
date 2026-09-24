package org.portolan;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;

/** A loaded Portolan catalog root. */
public final class PortolanCatalog extends PortolanDocument {
  private PortolanCatalog(ObjectNode data, URI href) {
    super(data, href);
  }

  public static PortolanCatalog open(Path source) {
    Path path = Files.isDirectory(source) ? source.resolve("catalog.json") : source;
    URI href = path.toAbsolutePath().normalize().toUri();
    return new PortolanCatalog(JsonSupport.readObject(href), href);
  }

  public static PortolanCatalog open(URI source) {
    return new PortolanCatalog(JsonSupport.readObject(source), source);
  }

  public List<PortolanCollection> collections() {
    List<PortolanCollection> result = new ArrayList<>();
    collectCollections(data, href, new HashSet<>(), result);
    return List.copyOf(result);
  }

  private static void collectCollections(
      ObjectNode data, URI href, Set<URI> visited, List<PortolanCollection> result) {
    if (!visited.add(href)) {
      return;
    }
    PortolanCatalog current = new PortolanCatalog(data, href);
    for (PortolanLink link : current.links()) {
      if (!"child".equals(link.rel())) {
        continue;
      }
      ObjectNode child = JsonSupport.readObject(link.href());
      String childType = JsonSupport.text(child, "type");
      if ("Collection".equals(childType)) {
        result.add(new PortolanCollection(child, link.href()));
      } else if ("Catalog".equals(childType)) {
        collectCollections(child, link.href(), visited, result);
      }
    }
  }
}
