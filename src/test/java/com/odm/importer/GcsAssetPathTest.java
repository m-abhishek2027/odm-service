package com.odm.importer;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GcsAssetPathTest {

	private static final String PREFIX = "products/";

	@Test
	void parsesPathWithAssetTypeFolder() {
		GcsAssetPath path = GcsAssetPath.parse(
				"products/2026/03/16/external/autocomp/bosch/stills/15u/i5u/batch7/13cb25186_01_009.jpg", PREFIX,
				"image/jpeg").orElseThrow();

		assertThat(path.folderDate()).isEqualTo(LocalDate.of(2026, 3, 16));
		assertThat(path.source()).isEqualTo("external");
		assertThat(path.supplier()).isEqualTo("autocomp");
		assertThat(path.brand()).isEqualTo("bosch");
		assertThat(path.assetType()).isEqualTo("stills");
		assertThat(path.fileName()).isEqualTo("13cb25186_01_009.jpg");
		assertThat(path.fileNameWithoutExtension()).isEqualTo("13cb25186_01_009");
	}

	@Test
	void typeFolderWinsOverContentType() {
		// a zip stored under stills/ is still a stills asset
		GcsAssetPath path = GcsAssetPath.parse(
				"products/2026/03/16/external/autocomp/bosch/stills/15u/i5u/Images/360s.zip", PREFIX,
				"application/zip").orElseThrow();

		assertThat(path.assetType()).isEqualTo("stills");
	}

	@Test
	void typeFolderIsCaseInsensitive() {
		assertThat(GcsAssetPath.parse("products/2026/03/16/external/s/b/Video/clip.mp4", PREFIX, null)
				.orElseThrow().assetType()).isEqualTo("video");
	}

	@Test
	void pathWithoutTypeFolderInfersTypeFromContentType() {
		GcsAssetPath path = GcsAssetPath.parse("products/2026/03/16/external/delphiauto/delphi/0bc/k4y/0bck4ymbo.jpg",
				PREFIX, "image/jpeg").orElseThrow();

		assertThat(path.brand()).isEqualTo("delphi");
		assertThat(path.assetType()).isEqualTo("stills");
		assertThat(path.fileName()).isEqualTo("0bck4ymbo.jpg");
	}

	@Test
	void fileDirectlyUnderBrandUsesContentType() {
		GcsAssetPath path = GcsAssetPath.parse("products/2026/03/16/external/s/b/manual.pdf", PREFIX,
				"application/pdf").orElseThrow();

		assertThat(path.assetType()).isEqualTo("document");
	}

	@Test
	void unknownTypeIsNull() {
		assertThat(GcsAssetPath.parse("products/2026/03/16/external/s/b/x/data.bin", PREFIX,
				"application/octet-stream").orElseThrow().assetType()).isNull();
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"products/2026/03/16/external/autocomp/bosch/",           // folder placeholder
			"products/2026/03/16/external/autocomp/x.jpg",            // no brand
			"products/2026/13/16/external/autocomp/bosch/x.jpg",      // invalid month
			"products/26/03/16/external/autocomp/bosch/x.jpg",        // 2-digit year
			"products/2026/3/16/external/autocomp/bosch/x.jpg",       // 1-digit month
			"products/abcd/03/16/external/autocomp/bosch/x.jpg",      // not a date
			"products/2026/03/16/external//bosch/x.jpg",              // empty segment
			"other/2026/03/16/external/autocomp/bosch/x.jpg"          // outside prefix
	})
	void rejectsPathsOutsideTheLayout(String objectName) {
		assertThat(GcsAssetPath.parse(objectName, PREFIX, "image/jpeg")).isEmpty();
	}

	@Test
	void detectsSystemFiles() {
		assertThat(GcsAssetPath.isSystemFile("Thumbs.db")).isTrue();
		assertThat(GcsAssetPath.isSystemFile(".DS_Store")).isTrue();
		assertThat(GcsAssetPath.isSystemFile("360s.zip")).isFalse();
	}

}
