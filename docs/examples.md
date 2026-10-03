# Examples

These examples use the public Java API. They do not invoke a CLI or read the
contents of geospatial assets.

## Add the Maven dependency

GitHub Packages hosts tagged versions. Add its repository and the dependency:

```xml
<repositories>
  <repository>
    <id>github</id>
    <url>https://maven.pkg.github.com/jemacchi/portolan-java</url>
  </repository>
</repositories>

<dependencies>
  <dependency>
    <groupId>org.portolan</groupId>
    <artifactId>portolan-java</artifactId>
    <version>0.1.2</version>
  </dependency>
</dependencies>
```

GitHub Packages requires authentication, including for public packages. See
[Distribution](distribution.md) for the Maven settings entry.

## List collections and assets

```java
import java.nio.file.Path;
import org.portolan.PortolanCatalog;

PortolanCatalog catalog = PortolanCatalog.open(Path.of("./catalog"));

catalog.collections().forEach(collection -> {
  System.out.println(collection.id());

  collection.assets().forEach(asset -> {
    System.out.println(asset.key());
    System.out.println(asset.href());
    System.out.println(asset.mediaType());
    System.out.println(asset.format());
  });
});
```

`PortolanCatalog.open(Path)` accepts a catalog directory or a path to
`catalog.json`.

## Validate a catalog

```java
import java.nio.file.Path;
import org.portolan.PortolanCatalog;
import org.portolan.PortolanValidator;

PortolanCatalog catalog = PortolanCatalog.open(Path.of("./catalog"));
var result = PortolanValidator.validate(catalog);

if (!result.valid()) {
  result.errors().forEach(error -> {
    System.out.println(error.code() + " " + error.path() + " " + error.message());
  });
}
```

The validator returns structured errors rather than printing directly.

## Build an asset inventory

This report groups every collection asset by its declared or inferred format:

```java
import java.net.URI;
import java.util.EnumMap;
import java.util.Map;
import org.portolan.AssetFormat;
import org.portolan.PortolanCatalog;

var catalog = PortolanCatalog.open(URI.create("https://example.com/catalog.json"));
Map<AssetFormat, Integer> totals = new EnumMap<>(AssetFormat.class);

catalog.collections().forEach(collection ->
    collection.assets().forEach(asset -> {
      totals.merge(asset.format(), 1, Integer::sum);
      System.out.printf(
          "%-24s %-12s %s%n", collection.id(), asset.format(), asset.href());
    }));

System.out.println("Inventory: " + totals);
```

Format detection uses metadata and file extensions. It does not download
GeoParquet, COG, or PMTiles data.

## Load registry entries

```java
import java.util.List;
import java.util.Set;
import org.portolan.PortolanRegistry;

var entries =
    PortolanRegistry.loadRegistryEntries(
        PortolanRegistry.DEFAULT_REGISTRY_URL,
        null,
        Set.of(),
        false,
        5);

entries.forEach(entry -> System.out.println(entry.id() + " " + entry.url()));
```

Pass a custom fetcher in tests when you do not want network access.

## Find and validate one registry catalog

Use the registry as an index, then open the selected catalog through the same
API used for local files:

```java
import java.net.URI;
import java.util.Set;
import org.portolan.PortolanCatalog;
import org.portolan.PortolanRegistry;
import org.portolan.PortolanValidator;

var matches = PortolanRegistry.loadRegistryEntries(
    PortolanRegistry.DEFAULT_REGISTRY_URL,
    null,
    Set.of("my-catalog"),
    false,
    1);

if (matches.isEmpty()) {
  throw new IllegalStateException("Catalog not found");
}

var catalog = PortolanCatalog.open(URI.create(matches.get(0).url()));
var result = PortolanValidator.validate(catalog);

System.out.printf("%s: %s%n", catalog.id(), result.valid() ? "valid" : "invalid");
result.errors().forEach(error ->
    System.out.printf("  %s at %s: %s%n", error.code(), error.path(), error.message()));
```

Replace `my-catalog` with an ID from the registry export.
