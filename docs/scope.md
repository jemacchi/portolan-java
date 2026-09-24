# Scope

`portolan-java` is the JVM counterpart of `portolan-python`. It gives Java
applications a native API for Portolan catalog semantics.

The library reads and navigates Portolan/STAC metadata. It does not implement
GeoParquet, COG, PMTiles, or GeoServer behavior.

## Belongs here

- Opening a Portolan catalog from a `Path` or `URI`.
- Navigating child collections, item links, assets, and links.
- Resolving HREFs relative to the document that declares them.
- Classifying assets from metadata.
- Validating metadata structure.
- Reading registry exports and catalog snapshots.

## Belongs outside

- Reading feature rows from GeoParquet.
- Reading raster pixels from COG.
- Publishing resources to GeoServer.
- Converting data into cloud-native formats.
- Managing server processes or GIS projects.

Consumers can pair this library with GeoTools, GeoServer, Spark, or other JVM
tools when they need data processing.
