package com.odm.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.odm.entity.ColorSpace;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProductRequest {

	// source, supplier, brand and assetName are required on create only;
	// on update a blank value keeps the stored one

	@Size(max = 255)
	private String title;

	private String description;

	@Size(max = 255)
	private String productUpc;

	@Size(max = 255)
	private String productPartID;

	@Size(max = 255)
	private String rights;

	@Size(max = 255)
	private String mfg;

	@NotBlank(message = "source is required", groups = OnCreate.class)
	@Size(max = 255)
	private String source;

	@NotBlank(message = "supplier is required", groups = OnCreate.class)
	@Size(max = 255)
	private String supplier;

	@NotBlank(message = "brand is required", groups = OnCreate.class)
	@Size(max = 255)
	private String brand;

	private ColorSpace colorSpace;

	@Size(max = 255)
	private String includeInSpinSet;

	@Size(max = 255)
	private String productRepresentation;

	@Size(max = 255)
	private String viewImageStyle;

	@Size(max = 255)
	private String viewLayout;

	@Size(max = 255)
	private String viewOrientation;

	private List<@Size(max = 255) String> language;

	private List<@Size(max = 255) String> productRegion;

	private List<@Size(max = 255) String> channelName;

	private List<@Size(max = 255) String> productBrandID;

	private List<@Size(max = 255) String> productSubBrand;

	private List<@Size(max = 255) String> productLine;

	private List<@Size(max = 255) String> productCategory;

	private List<@Size(max = 255) String> productType;

	// ==================== Scorecard Fields ====================

	@Size(max = 255)
	private String origAssetName;

	@Size(max = 255)
	private String adjAssetName;

	@Size(max = 255)
	private String origFileType;

	@Size(max = 255)
	private String adjFileType;

	@Size(max = 255)
	private String assetType;

	@Size(max = 255)
	private String fileSource;

	@PositiveOrZero
	private Integer origPixelSizeWidth;

	@PositiveOrZero
	private Integer origPixelSizeHeight;

	@PositiveOrZero
	private Integer croppedPixelSizeWidth;

	@PositiveOrZero
	private Integer croppedPixelSizeHeight;

	@PositiveOrZero
	private Integer adjPixelSizeWidth;

	@PositiveOrZero
	private Integer adjPixelSizeHeight;

	@PositiveOrZero
	private Double dpi;

	@PositiveOrZero
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

	// ==================== Digital Asset Fields ====================

	@NotBlank(message = "assetName is required", groups = OnCreate.class)
	@Size(max = 255)
	private String assetName;

	@Size(max = 2048)
	private String assetUrl;

	@Size(max = 255)
	private String assetSubCategory;

	// null means active on create and unchanged on update
	private Boolean active;

}
