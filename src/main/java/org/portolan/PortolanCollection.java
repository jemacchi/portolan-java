package org.portolan;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/** A loaded STAC Collection within a Portolan catalog. */
public final class PortolanCollection extends PortolanDocument {
  PortolanCollection(ObjectNode data, URI href) {
    super(data, href);
  }

  public List<PortolanItem> items() {
    List<PortolanItem> result = new ArrayList<>();
    for (PortolanLink link : links()) {
      if (!"item".equals(link.rel())) {
        continue;
      }
      ObjectNode item = JsonSupport.readObject(link.href());
      if ("Feature".equals(JsonSupport.text(item, "type"))) {
        result.add(new PortolanItem(item, link.href()));
      }
    }
    return List.copyOf(result);
  }
}
