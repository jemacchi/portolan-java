package org.portolan;

import java.util.List;

/** One catalog entry from a Portolan registry export. */
public record RegistryCatalogEntry(
    String id,
    String url,
    String title,
    String status,
    List<Double> bbox,
    List<String> licenses,
    Long collectionCount,
    Long featureCount,
    Long totalSizeBytes,
    String updated,
    String logoUrl,
    String failureReason) {

  public RegistryCatalogEntry {
    bbox = bbox == null ? null : List.copyOf(bbox);
    licenses = licenses == null ? List.of() : List.copyOf(licenses);
  }

  /** Create an entry without optional registry summary metadata. */
  public RegistryCatalogEntry(String id, String url, String title, String status) {
    this(id, url, title, status, null, List.of(), null, null, null, null, null, null);
  }
}
