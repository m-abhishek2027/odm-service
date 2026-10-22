package com.odm.dto;

import java.time.LocalDateTime;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

// Query parameters of GET /digitalAsset. Every filter is optional; set filters are ANDed.
@Data
public class DigitalAssetSearchCriteria {

	@Min(0)
	private long offset = 0;

	@Min(1)
	@Max(500)
	private int limit = 20;

	private Boolean active;

	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
	private LocalDateTime createdOnFrom;

	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
	private LocalDateTime createdOnTo;

	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
	private LocalDateTime modifiedOnFrom;

	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
	private LocalDateTime modifiedOnTo;

	private String createdBy;

	private String modifiedBy;

	// partial, case-insensitive match
	private String assetName;

	// partial, case-insensitive match
	private String assetUrl;

	private String assetSubCategory;

	// exact match
	private String source;

	// exact match
	private String supplier;

	// exact match
	private String brand;

	// exact match
	private String origAssetName;

}
