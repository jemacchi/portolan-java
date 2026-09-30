package org.portolan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonSupportTest {
  @TempDir Path tempDir;

  @Test
  void readsObjectsFromStringsAndExtractsTextFields() {
    ObjectNode object = JsonSupport.readObject("{\"id\":\"roads\",\"count\":2}");

    assertEquals("roads", JsonSupport.text(object, "id"));
    assertEquals(null, JsonSupport.text(object, "count"));
    assertEquals(null, JsonSupport.text(object, "missing"));
  }

  @Test
  void rejectsInvalidAndNonObjectStrings() {
    assertEquals(
        "Expected JSON object",
        assertThrows(IllegalArgumentException.class, () -> JsonSupport.readObject("[]"))
            .getMessage());
    assertEquals(
        "Invalid JSON",
        assertThrows(IllegalArgumentException.class, () -> JsonSupport.readObject("{"))
            .getMessage());
  }

  @Test
  void readsFileAndSchemeLessUris() throws IOException {
    Path source = tempDir.resolve("catalog.json");
    Files.writeString(source, "{\"id\":\"demo\"}");

    assertEquals("demo", JsonSupport.readObject(source.toUri()).path("id").asText());
    assertEquals(
        "demo", JsonSupport.readObject(URI.create(source.toString())).path("id").asText());
  }

  @Test
  void reportsFileReadAndWriteFailures() throws IOException {
    IllegalArgumentException readFailure =
        assertThrows(
            IllegalArgumentException.class,
            () -> JsonSupport.readObject(tempDir.resolve("missing.json").toUri()));
    assertTrue(readFailure.getMessage().startsWith("Cannot read JSON from"));
    assertInstanceOf(IOException.class, readFailure.getCause());

    Path regularFile = tempDir.resolve("not-a-directory");
    Files.writeString(regularFile, "content");
    IllegalArgumentException writeFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                JsonSupport.writeObject(
                    regularFile.resolve("catalog.json"), JsonSupport.readObject("{}")));
    assertTrue(writeFailure.getMessage().startsWith("Cannot write JSON to"));
    assertInstanceOf(IOException.class, writeFailure.getCause());
  }

  @Test
  void writesObjectsAndCreatesParentDirectories() throws IOException {
    Path target = tempDir.resolve("nested/catalog.json");

    JsonSupport.writeObject(target, JsonSupport.readObject("{\"id\":\"demo\"}"));

    assertEquals("demo", JsonSupport.readObject(target.toUri()).path("id").asText());
  }

  @Test
  void readsHttpObjectsAndRejectsInvalidResponses() throws IOException {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/catalog.json",
        exchange -> {
          byte[] body = "{\"id\":\"remote\"}".getBytes(StandardCharsets.UTF_8);
          assertEquals("portolan-java", exchange.getRequestHeaders().getFirst("User-Agent"));
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.createContext(
        "/array.json",
        exchange -> {
          byte[] body = "[]".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.createContext(
        "/missing.json",
        exchange -> {
          byte[] body = "{\"error\":\"missing\"}".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(404, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
    try {
      URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());

      assertEquals(
          "remote", JsonSupport.readObject(base.resolve("/catalog.json")).path("id").asText());
      assertEquals(
          "Expected JSON object from " + base.resolve("/array.json"),
          assertThrows(
                  IllegalArgumentException.class,
                  () -> JsonSupport.readObject(base.resolve("/array.json")))
              .getMessage());
      assertTrue(
          assertThrows(
                  IllegalArgumentException.class,
                  () -> JsonSupport.readObject(base.resolve("/missing.json")))
              .getMessage()
              .contains("HTTP 404"));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void preservesTheInterruptFlagWhenHttpReadingIsInterrupted() {
    Thread.currentThread().interrupt();
    try {
      IllegalArgumentException failure =
          assertThrows(
              IllegalArgumentException.class,
              () -> JsonSupport.readObject(URI.create("http://127.0.0.1:1/catalog.json")));

      assertTrue(failure.getMessage().startsWith("Interrupted reading JSON from"));
      assertInstanceOf(InterruptedException.class, failure.getCause());
      assertTrue(Thread.currentThread().isInterrupted());
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void recognizesHttpsUrisBeforeReportingTransportFailures() {
    IllegalArgumentException failure =
        assertThrows(
            IllegalArgumentException.class,
            () -> JsonSupport.readObject(URI.create("https://127.0.0.1:1/catalog.json")));

    assertTrue(failure.getMessage().startsWith("Cannot read JSON from https://"));
    assertInstanceOf(IOException.class, failure.getCause());
  }
}
