package com.odm.metadata;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.function.Function;

import com.odm.dto.MetadataUpdate;
import com.odm.entity.ColorSpace;

/**
 * The 25 AEM-owned metadata fields, in one place: JSON name, products column, Java type and how to read the value
 * from a request. Nothing outside this list can be written by the metadata update endpoints.
 */
public enum MetadataField {

	ORIG_ASSET_NAME("origAssetName", "orig_asset_name", String.class, MetadataUpdate::getOrigAssetName),
	ADJ_ASSET_NAME("adjAssetName", "adj_asset_name", String.class, MetadataUpdate::getAdjAssetName),
	ORIG_FILE_TYPE("origFileType", "orig_file_type", String.class, MetadataUpdate::getOrigFileType),
	ADJ_FILE_TYPE("adjFileType", "adj_file_type", String.class, MetadataUpdate::getAdjFileType),
	ASSET_TYPE("assetType", "asset_type", String.class, MetadataUpdate::getAssetType),
	FILE_SOURCE("fileSource", "file_source", String.class, MetadataUpdate::getFileSource),
	ORIG_PIXEL_SIZE_WIDTH("origPixelSizeWidth", "orig_pixel_size_width", Integer.class, MetadataUpdate::getOrigPixelSizeWidth),
	ORIG_PIXEL_SIZE_HEIGHT("origPixelSizeHeight", "orig_pixel_size_height", Integer.class, MetadataUpdate::getOrigPixelSizeHeight),
	CROPPED_PIXEL_SIZE_WIDTH("croppedPixelSizeWidth", "cropped_pixel_size_width", Integer.class, MetadataUpdate::getCroppedPixelSizeWidth),
	CROPPED_PIXEL_SIZE_HEIGHT("croppedPixelSizeHeight", "cropped_pixel_size_height", Integer.class, MetadataUpdate::getCroppedPixelSizeHeight),
	ADJ_PIXEL_SIZE_WIDTH("adjPixelSizeWidth", "adj_pixel_size_width", Integer.class, MetadataUpdate::getAdjPixelSizeWidth),
	ADJ_PIXEL_SIZE_HEIGHT("adjPixelSizeHeight", "adj_pixel_size_height", Integer.class, MetadataUpdate::getAdjPixelSizeHeight),
	DPI("dpi", "dpi", Double.class, MetadataUpdate::getDpi),
	FILE_SIZE_IN_KB("fileSizeInKB", "file_size_in_kb", Double.class, MetadataUpdate::getFileSizeInKB),
	IS_LAYERED("isLayered", "is_layered", Boolean.class, MetadataUpdate::getIsLayered),
	HAS_WATERMARK("hasWatermark", "has_watermark", Boolean.class, MetadataUpdate::getHasWatermark),
	OK_THUMB("okThumb", "ok_thumb", Boolean.class, MetadataUpdate::getOkThumb),
	OK_SMALL("okSmall", "ok_small", Boolean.class, MetadataUpdate::getOkSmall),
	OK_MEDIUM("okMedium", "ok_medium", Boolean.class, MetadataUpdate::getOkMedium),
	OK_LARGE("okLarge", "ok_large", Boolean.class, MetadataUpdate::getOkLarge),
	OK_EXTRA_LARGE("okExtraLarge", "ok_extra_large", Boolean.class, MetadataUpdate::getOkExtraLarge),
	OK_WHITESPACE("okWhitespace", "ok_whitespace", Boolean.class, MetadataUpdate::getOkWhitespace),
	OK_LIGHT_DARK("okLightDark", "ok_light_dark", Boolean.class, MetadataUpdate::getOkLightDark),
	DATE_PROCESSED("dateProcessed", "date_processed", LocalDateTime.class, MetadataUpdate::getDateProcessed),
	COLOR_SPACE("colorSpace", "color_space", ColorSpace.class, MetadataUpdate::getColorSpace);

	private final String jsonName;

	private final String column;

	private final Class<?> type;

	private final Function<MetadataUpdate, Object> getter;

	MetadataField(String jsonName, String column, Class<?> type, Function<MetadataUpdate, ?> getter) {
		this.jsonName = jsonName;
		this.column = column;
		this.type = type;
		this.getter = getter::apply;
	}

	public String jsonName() {
		return jsonName;
	}

	public String column() {
		return column;
	}

	/** @return the value the request asks for, or null when it leaves the field unchanged (missing or blank) */
	public Object requested(MetadataUpdate update) {
		Object value = getter.apply(update);
		if (value instanceof String s && s.isBlank()) {
			return null;
		}
		// datetime(6) keeps microseconds; finer precision would make every comparison look like a change
		return value instanceof LocalDateTime t ? t.truncatedTo(ChronoUnit.MICROS) : value;
	}

	/** @return the stored value, in the same Java type as {@link #requested} */
	public Object read(ResultSet rs) throws SQLException {
		if (type == ColorSpace.class) {
			String stored = rs.getString(column);
			if (stored == null) {
				return null;
			}
			try {
				return ColorSpace.valueOf(stored);
			} catch (IllegalArgumentException ex) {
				// a value outside the enum (written before it existed) differs from any requested value
				return stored;
			}
		}
		return rs.getObject(column, type);
	}

	/** @return the value as a JDBC parameter */
	public static Object toJdbc(Object value) {
		return value instanceof Enum<?> e ? e.name() : value;
	}

}
