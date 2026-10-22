package com.odm.dto;

import com.odm.entity.Product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Business key used by POST /digitalAsset/lookup; all four fields must match
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssetLookupKey {

	@NotBlank(message = "source is required")
	@Size(max = 255)
	private String source;

	@NotBlank(message = "supplier is required")
	@Size(max = 255)
	private String supplier;

	@NotBlank(message = "brand is required")
	@Size(max = 255)
	private String brand;

	@NotBlank(message = "origAssetName is required")
	@Size(max = 255)
	private String origAssetName;

	public static AssetLookupKey of(Product product) {
		return new AssetLookupKey(product.getSource(), product.getSupplier(), product.getBrand(),
				product.getOrigAssetName());
	}

	public AssetLookupKey trimmed() {
		return new AssetLookupKey(trim(source), trim(supplier), trim(brand), trim(origAssetName));
	}

	private static String trim(String value) {
		return value == null ? null : value.trim();
	}

}
