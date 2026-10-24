package com.odm.metadata;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.odm.dto.MetadataUpdate;
import com.odm.entity.ColorSpace;
import com.odm.entity.Product;

import jakarta.persistence.Column;

class MetadataFieldTest {

	private static final Set<String> NOT_METADATA = Set.of("uuid", "metadataModifiedAt", "unknownFields");

	@Test
	void everyRequestPropertyIsAMetadataFieldAndViceVersa() {
		Set<String> requestFields = Arrays.stream(MetadataUpdate.class.getDeclaredFields())
				.filter(f -> !Modifier.isStatic(f.getModifiers()))
				.map(Field::getName)
				.filter(n -> !NOT_METADATA.contains(n))
				.collect(Collectors.toSet());
		Set<String> metadataFields = Arrays.stream(MetadataField.values()).map(MetadataField::jsonName)
				.collect(Collectors.toSet());

		assertThat(metadataFields).hasSize(25).isEqualTo(requestFields);
	}

	@Test
	void everyColumnMatchesTheEntityMapping() throws Exception {
		for (MetadataField field : MetadataField.values()) {
			Column column = Product.class.getDeclaredField(field.jsonName()).getAnnotation(Column.class);
			assertThat(column.name()).as(field.name()).isEqualTo(field.column());
		}
	}

	@Test
	void keyFieldsAreNeverUpdatable() {
		Set<String> columns = Arrays.stream(MetadataField.values()).map(MetadataField::column).collect(Collectors.toSet());

		assertThat(columns).doesNotContain("uuid", "source", "supplier", "brand", "asset_name", "lookup_key_hash",
				"active", "title", "description");
	}

	@Test
	void blankStringsMeanNoChangeAndTimesAreTruncatedToMicroseconds() {
		MetadataUpdate u = new MetadataUpdate();
		u.setFileSource("  ");
		u.setDateProcessed(LocalDateTime.of(2026, 1, 1, 0, 0, 0, 123_456_789));
		u.setColorSpace(ColorSpace.WEB);

		assertThat(MetadataField.FILE_SOURCE.requested(u)).isNull();
		assertThat(MetadataField.DATE_PROCESSED.requested(u)).isEqualTo(LocalDateTime.of(2026, 1, 1, 0, 0, 0, 123_456_000));
		assertThat(MetadataField.toJdbc(MetadataField.COLOR_SPACE.requested(u))).isEqualTo("WEB");
	}

}
