package com.odm.entity;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.odm.lookup.LookupKey;

import org.hibernate.annotations.ColumnDefault;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "products")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Product {

	@Id
	@Column(name = "uuid", unique = true, nullable = false, updatable = false, length = 36)
	private String uuid;
	
	@PrePersist
	public void prePersist() {
	    if (uuid == null || uuid.isBlank()) {
	        uuid = UUID.randomUUID().toString();
	    }
	    if (active == null) {
	        active = Boolean.TRUE;
	    }
	    refreshLookupKeyHash();
	}

	@PreUpdate
	public void preUpdate() {
	    refreshLookupKeyHash();
	}

	// keeps lookup_key_hash in step with the four key fields on every JPA insert and update
	private void refreshLookupKeyHash() {
	    lookupKeyHash = LookupKey.hashOf(source, supplier, brand, assetName);
	}

	@Column(name = "title")
	private String title;

	@Column(name = "description", columnDefinition = "TEXT")
	private String description;

	@Column(name = "product_upc")
	private String productUpc;

	@Column(name = "product_part_id")
	private String productPartID;

	private String rights;

	private String mfg;

	private String source;

	private String supplier;

	private String brand;

	@Enumerated(EnumType.STRING)
	@Column(name = "color_space")
	private ColorSpace colorSpace;

	@Column(name = "include_in_spin_set")
	private String includeInSpinSet;

	@Column(name = "product_representation")
	private String productRepresentation;

	@Column(name = "view_image_style")
	private String viewImageStyle;

	@Column(name = "view_layout")
	private String viewLayout;

	@Column(name = "view_orientation")
	private String viewOrientation;

	@ElementCollection
	@CollectionTable(name = "product_languages", joinColumns = @JoinColumn(name = "product_id"))
	@Column(name = "language")
	private List<String> language;

	@ElementCollection
	@CollectionTable(name = "product_regions", joinColumns = @JoinColumn(name = "product_id"))
	@Column(name = "region")
	private List<String> productRegion;

	@ElementCollection
	@CollectionTable(name = "product_channels", joinColumns = @JoinColumn(name = "product_id"))
	@Column(name = "channel_name")
	private List<String> channelName;

	@ElementCollection
	@CollectionTable(name = "product_brand_ids", joinColumns = @JoinColumn(name = "product_id"))
	@Column(name = "brand_id")
	private List<String> productBrandID;

	@ElementCollection
	@CollectionTable(name = "product_sub_brands", joinColumns = @JoinColumn(name = "product_id"))
	@Column(name = "sub_brand")
	private List<String> productSubBrand;

	@ElementCollection
	@CollectionTable(name = "product_lines", joinColumns = @JoinColumn(name = "product_id"))
	@Column(name = "product_line")
	private List<String> productLine;

	@ElementCollection
	@CollectionTable(name = "product_categories", joinColumns = @JoinColumn(name = "product_id"))
	@Column(name = "product_category")
	private List<String> productCategory;

	@ElementCollection
	@CollectionTable(name = "product_types", joinColumns = @JoinColumn(name = "product_id"))
	@Column(name = "product_type")
	private List<String> productType;
	
	// ==================== Scorecard Fields ====================

	@Column(name = "orig_asset_name")
	private String origAssetName;

	@Column(name = "adj_asset_name")
	private String adjAssetName;

	@Column(name = "orig_file_type")
	private String origFileType;

	@Column(name = "adj_file_type")
	private String adjFileType;

	@Column(name = "asset_type")
	private String assetType;

	@Column(name = "file_source")
	private String fileSource;

	@Column(name = "orig_pixel_size_width")
	private Integer origPixelSizeWidth;

	@Column(name = "orig_pixel_size_height")
	private Integer origPixelSizeHeight;

	@Column(name = "cropped_pixel_size_width")
	private Integer croppedPixelSizeWidth;

	@Column(name = "cropped_pixel_size_height")
	private Integer croppedPixelSizeHeight;

	@Column(name = "adj_pixel_size_width")
	private Integer adjPixelSizeWidth;

	@Column(name = "adj_pixel_size_height")
	private Integer adjPixelSizeHeight;

	@Column(name = "dpi")
	private Double dpi;

	@Column(name = "file_size_in_kb")
	private Double fileSizeInKB;

	@Column(name = "is_layered")
	private Boolean isLayered;

	@Column(name = "has_watermark")
	private Boolean hasWatermark;

	@Column(name = "ok_thumb")
	private Boolean okThumb;

	@Column(name = "ok_small")
	private Boolean okSmall;

	@Column(name = "ok_medium")
	private Boolean okMedium;

	@Column(name = "ok_large")
	private Boolean okLarge;

	@Column(name = "ok_extra_large")
	private Boolean okExtraLarge;

	@Column(name = "ok_whitespace")
	private Boolean okWhitespace;

	@Column(name = "ok_light_dark")
	private Boolean okLightDark;

	@Column(name = "date_processed")
	private LocalDateTime dateProcessed;

	// ==================== Digital Asset Fields ====================

	@Column(name = "asset_name")
	private String assetName;

	// ==================== Lookup Fields ====================

	// SHA-256 of the normalized (source, supplier, brand, assetName); see LookupKey
	@JsonIgnore
	@Column(name = "lookup_key_hash", length = 64)
	private String lookupKeyHash;

	// MD5 reported by Cloud Storage (base64); tells identical copies from different files in lookup
	@JsonIgnore
	@Column(name = "content_md5", length = 32)
	private String contentMd5;

	@Column(name = "asset_url", length = 2048)
	private String assetUrl;

	@Column(name = "asset_sub_category")
	private String assetSubCategory;

	// ==================== Audit Fields ====================

	// false means soft-deleted
	@ColumnDefault("1")
	@Column(name = "active")
	private Boolean active;

	@CreatedDate
	@Column(name = "created_on", updatable = false)
	private LocalDateTime createdOn;

	@CreatedBy
	@Column(name = "created_by", updatable = false)
	private String createdBy;

	@LastModifiedDate
	@Column(name = "modified_on")
	private LocalDateTime modifiedOn;

	@LastModifiedBy
	@Column(name = "modified_by")
	private String modifiedBy;

}