package org.portolan;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PortolanAssetTest {
  @ParameterizedTest(name = "{0}")
  @MethodSource("formats")
  void classifiesKnownAssetFormats(
      String description, URI href, String mediaType, AssetFormat expected) {
    PortolanAsset asset =
        new PortolanAsset("data", href, mediaType, List.of(), null, null, null);

    assertEquals(expected, asset.format());
  }

  private static Stream<Arguments> formats() {
    return Stream.of(
        Arguments.of(
            "GeoParquet media type",
            URI.create("https://example.test/data"),
            "application/vnd.apache.parquet",
            AssetFormat.GEOPARQUET),
        Arguments.of(
            "GeoParquet extension",
            URI.create("https://example.test/DATA.PARQUET"),
            null,
            AssetFormat.GEOPARQUET),
        Arguments.of(
            "COG media type",
            URI.create("https://example.test/image"),
            "image/tiff; application=geotiff; profile=cloud-optimized",
            AssetFormat.COG),
        Arguments.of(
            "TIF extension", URI.create("https://example.test/image.tif"), null, AssetFormat.COG),
        Arguments.of(
            "TIFF extension", URI.create("https://example.test/image.TIFF"), null, AssetFormat.COG),
        Arguments.of(
            "PMTiles media type",
            URI.create("https://example.test/tiles"),
            "application/vnd.pmtiles",
            AssetFormat.PMTILES),
        Arguments.of(
            "PMTiles extension",
            URI.create("https://example.test/TILES.PMTILES"),
            null,
            AssetFormat.PMTILES),
        Arguments.of(
            "unknown extension",
            URI.create("https://example.test/readme.txt"),
            "text/plain",
            AssetFormat.UNKNOWN),
        Arguments.of("null href", null, null, AssetFormat.UNKNOWN),
        Arguments.of("opaque href", URI.create("urn:portolan:asset"), null, AssetFormat.UNKNOWN));
  }
}
