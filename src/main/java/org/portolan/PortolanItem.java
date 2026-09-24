package org.portolan;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;

/** A loaded STAC Item. */
public final class PortolanItem extends PortolanDocument {
  PortolanItem(ObjectNode data, URI href) {
    super(data, href);
  }
}
