package com.odm.dto;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.odm.entity.ColorSpace;
import com.odm.entity.Product;

/**
 * One asset in the lookup response, shaped for AEM's AssetMetadataResponseItem (applyMetadata reads the list
 * fields as arrays). uuid identifies the asset for later PUT /digitalAsset/{uuid} calls. fileName is the
 * assetName the lookup matched on. Null fields are left out of the JSON.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AssetMetadataItem(
		String uuid,
		String fileName,
		String source,
		String supplier,
		String brand,
		String assetType,
		String title,
		String description,
		String productUpc,
		String productPartID,
		String rights,
		String mfg,
		ColorSpace colorSpace,
		String includeInSpinSet,
		String productRepresentation,
		String viewImageStyle,
		String viewLayout,
		String viewOrientation,
		List<String> language,
		List<String> productRegion,
		List<String> channelName,
		List<String> productBrandID,
		List<String> productSubBrand,
		// AEM reads this as getProductline()
		@JsonProperty("productline") List<String> productline,
		List<String> productCategory,
		List<String> productType) {

	// call inside the transaction: the list fields are lazy collections
	public static AssetMetadataItem from(Product p) {
		return new AssetMetadataItem(p.getUuid(), p.getAssetName(), p.getSource(), p.getSupplier(), p.getBrand(),
				p.getAssetType(), p.getTitle(), p.getDescription(), p.getProductUpc(), p.getProductPartID(),
				p.getRights(), p.getMfg(), p.getColorSpace(), p.getIncludeInSpinSet(), p.getProductRepresentation(),
				p.getViewImageStyle(), p.getViewLayout(), p.getViewOrientation(), copy(p.getLanguage()),
				copy(p.getProductRegion()), copy(p.getChannelName()), copy(p.getProductBrandID()),
				copy(p.getProductSubBrand()), copy(p.getProductLine()), copy(p.getProductCategory()),
				copy(p.getProductType()));
	}

	private static List<String> copy(List<String> values) {
		return values == null ? null : new ArrayList<>(values);
	}

}
