package com.odm.lookup;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LookupKeyTest {

	private static String hash(String source, String supplier, String brand, String fileName) {
		return LookupKey.hashOf(source, supplier, brand, fileName);
	}

	@Test
	void hashIsSixtyFourLowercaseHexAndStable() {
		String h = hash("external", "autocomp", "bosch", "13cb25186_01_009.jpg");

		assertThat(h).hasSize(64).matches("[0-9a-f]{64}");
		assertThat(hash("external", "autocomp", "bosch", "13cb25186_01_009.jpg")).isEqualTo(h);
	}

	@Test
	void ignoresCaseAndSurroundingWhitespace() {
		assertThat(hash("  EXTERNAL ", "AutoComp", "Bosch\t", " 13CB25186_01_009.JPG"))
				.isEqualTo(hash("external", "autocomp", "bosch", "13cb25186_01_009.jpg"));
	}

	@Test
	void keepsAccentsDistinct() {
		assertThat(hash("external", "autocomp", "bosch", "café.jpg"))
				.isNotEqualTo(hash("external", "autocomp", "bosch", "cafe.jpg"));
		assertThat(hash("external", "autocomp", "bosch", "straße.jpg"))
				.isNotEqualTo(hash("external", "autocomp", "bosch", "strasse.jpg"));
	}

	@Test
	void composedAndDecomposedUnicodeAreTheSameName() {
		String composed = "café.jpg";
		String decomposed = "café.jpg";

		assertThat(hash("s", "su", "b", decomposed)).isEqualTo(hash("s", "su", "b", composed));
	}

	@Test
	void fieldBoundariesMatter() {
		assertThat(hash("ab", "c", "bosch", "x.jpg")).isNotEqualTo(hash("a", "bc", "bosch", "x.jpg"));
	}

	@Test
	void innerSpacesMatter() {
		assertThat(hash("s", "su", "b", "my file.jpg")).isNotEqualTo(hash("s", "su", "b", "myfile.jpg"));
	}

	@Test
	void incompleteKeyHasNoHash() {
		assertThat(hash(null, "autocomp", "bosch", "x.jpg")).isNull();
		assertThat(hash("external", "  ", "bosch", "x.jpg")).isNull();
		assertThat(hash("external", "autocomp", "", "x.jpg")).isNull();
		assertThat(LookupKey.of("external", "autocomp", "bosch", null)).isNull();
	}

}
