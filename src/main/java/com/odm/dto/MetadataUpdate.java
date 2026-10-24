package com.odm.dto;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.odm.entity.ColorSpace;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AEM-generated metadata for one asset. Only these 25 fields can be written through the update endpoints; any
 * other property in the JSON makes the item INVALID. A field that is missing, null or blank leaves the stored
 * value unchanged. uuid identifies the item (checked with the request); everything else is checked per item.
 */
@Data
public class MetadataUpdate {

	// identifies the asset; in a batch it is required, for PUT /{uuid} it comes from the path
	@Size(max = 36, message = "uuid must be at most 36 characters")
	private String uuid;

	// when AEM generated this metadata (ISO-8601 with offset, e.g. 2026-10-25T10:15:30.123Z)
	@NotNull(message = "metadataModifiedAt is required", groups = MetadataItemChecks.class)
	private Instant metadataModifiedAt;

	@Size(max = 255, groups = MetadataItemChecks.class)
	private String origAssetName;

	@Size(max = 255, groups = MetadataItemChecks.class)
	private String adjAssetName;

	@Size(max = 255, groups = MetadataItemChecks.class)
	private String origFileType;

	@Size(max = 255, groups = MetadataItemChecks.class)
	private String adjFileType;

	@Size(max = 255, groups = MetadataItemChecks.class)
	private String assetType;

	@Size(max = 255, groups = MetadataItemChecks.class)
	private String fileSource;

	@PositiveOrZero(groups = MetadataItemChecks.class)
	private Integer origPixelSizeWidth;

	@PositiveOrZero(groups = MetadataItemChecks.class)
	private Integer origPixelSizeHeight;

	@PositiveOrZero(groups = MetadataItemChecks.class)
	private Integer croppedPixelSizeWidth;

	@PositiveOrZero(groups = MetadataItemChecks.class)
	private Integer croppedPixelSizeHeight;

	@PositiveOrZero(groups = MetadataItemChecks.class)
	private Integer adjPixelSizeWidth;

	@PositiveOrZero(groups = MetadataItemChecks.class)
	private Integer adjPixelSizeHeight;

	@PositiveOrZero(groups = MetadataItemChecks.class)
	private Double dpi;

	@PositiveOrZero(groups = MetadataItemChecks.class)
	private Double fileSizeInKB;

	private Boolean isLayered;

	private Boolean hasWatermark;

	private Boolean okThumb;

	private Boolean okSmall;

	private Boolean okMedium;

	private Boolean okLarge;

	private Boolean okExtraLarge;

	private Boolean okWhitespace;

	private Boolean okLightDark;

	private LocalDateTime dateProcessed;

	private ColorSpace colorSpace;

	// properties that are not updatable metadata (e.g. title, source); reported back as errors
	@JsonIgnore
	private final Map<String, Object> unknownFields = new LinkedHashMap<>();

	@JsonAnySetter
	public void addUnknownField(String name, Object value) {
		unknownFields.put(name, value);
	}

}
