package org.portolan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

final class JsonSupport {
  static final ObjectMapper MAPPER =
      new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

  private static final HttpClient HTTP = HttpClient.newHttpClient();

  private JsonSupport() {}

  static ObjectNode readObject(String json) {
    try {
      JsonNode node = MAPPER.readTree(json);
      if (!node.isObject()) {
        throw new IllegalArgumentException("Expected JSON object");
      }
      return (ObjectNode) node;
    } catch (IOException e) {
      throw new IllegalArgumentException("Invalid JSON", e);
    }
  }

  static ObjectNode readObject(URI href) {
    try {
      JsonNode node;
      if ("http".equals(href.getScheme()) || "https".equals(href.getScheme())) {
        HttpRequest request =
            HttpRequest.newBuilder(href).header("User-Agent", "portolan-java").GET().build();
        String body = HTTP.send(request, HttpResponse.BodyHandlers.ofString()).body();
        node = MAPPER.readTree(body);
      } else if ("file".equals(href.getScheme())) {
        node = MAPPER.readTree(Path.of(href).toFile());
      } else {
        node = MAPPER.readTree(Path.of(href.toString()).toFile());
      }
      if (!node.isObject()) {
        throw new IllegalArgumentException("Expected JSON object from " + href);
      }
      return (ObjectNode) node;
    } catch (IOException e) {
      throw new IllegalArgumentException("Cannot read JSON from " + href, e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalArgumentException("Interrupted reading JSON from " + href, e);
    }
  }

  static void writeObject(Path path, ObjectNode data) {
    try {
      Files.createDirectories(path.getParent());
      MAPPER.writeValue(path.toFile(), data);
    } catch (IOException e) {
      throw new IllegalArgumentException("Cannot write JSON to " + path, e);
    }
  }

  static String text(JsonNode node, String field) {
    JsonNode value = node.get(field);
    return value != null && value.isTextual() ? value.asText() : null;
  }
}
