package org.portolan;

/** One validation finding. */
public record ValidationError(String code, String path, String message) {}
