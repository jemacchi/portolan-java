package org.portolan;

import java.net.URI;

/** Resolves hrefs that can contain unescaped spaces from published catalog JSON. */
final class HrefResolver {
  private HrefResolver() {}

  static URI resolve(URI base, String href) {
    try {
      URI reference = URI.create(href.replace(" ", "%20"));
      return base.resolve(reference);
    } catch (IllegalArgumentException error) {
      throw new IllegalArgumentException("Invalid href '" + href + "' in " + base, error);
    }
  }
}
