package com.odm.service;

import java.util.List;

import com.odm.dto.AssetLookupKey;
import com.odm.dto.AssetLookupResult;
import com.odm.dto.DigitalAssetListResponse;
import com.odm.dto.DigitalAssetSearchCriteria;
import com.odm.dto.ProductRequest;
import com.odm.entity.Product;

public interface DigitalAssetMetadataService {

	DigitalAssetListResponse getDigitalAssets(DigitalAssetSearchCriteria criteria);

	List<Product> addDigitalAssets(List<ProductRequest> requests);

	Product getDigitalAsset(String uuid);

	Product updateDigitalAsset(String uuid, ProductRequest request);

	void deleteDigitalAsset(String uuid);

	List<AssetLookupResult> lookupDigitalAssets(List<AssetLookupKey> keys);

}
