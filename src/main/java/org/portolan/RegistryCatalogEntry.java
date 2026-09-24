package org.portolan;

/** One catalog entry from a Portolan registry export. */
public record RegistryCatalogEntry(String id, String url, String title, String status) {}
