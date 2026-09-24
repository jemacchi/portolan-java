# Examples

These examples assume a local Portolan catalog at `./catalog`.

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
