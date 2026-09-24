package org.portolan;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;

/** A STAC link with its HREF resolved against the containing document. */
public record PortolanLink(String rel, URI href, String mediaType, String title, ObjectNode raw) {}
