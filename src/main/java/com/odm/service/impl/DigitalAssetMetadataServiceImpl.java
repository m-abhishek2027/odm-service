package com.odm.service.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.hibernate.Hibernate;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.odm.dto.DigitalAssetListResponse;
import com.odm.dto.DigitalAssetSearchCriteria;
import com.odm.dto.ProductRequest;
import com.odm.entity.Product;
import com.odm.exception.ResourceNotFoundException;
import com.odm.repo.OffsetLimitPageable;
import com.odm.repo.ProductRepository;
import com.odm.repo.ProductSpecifications;
import com.odm.service.DigitalAssetMetadataService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DigitalAssetMetadataServiceImpl implements DigitalAssetMetadataService{

	private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("createdOn"), Sort.Order.asc("uuid"));

	private final ProductRepository productRepo;

	@Override
	@Transactional(readOnly = true)
	public DigitalAssetListResponse getDigitalAssets(DigitalAssetSearchCriteria criteria) {
		OffsetLimitPageable pageable = new OffsetLimitPageable(criteria.getOffset(), criteria.getLimit(), DEFAULT_SORT);
		Page<Product> page = productRepo.findAll(ProductSpecifications.matching(criteria), pageable);
		loadCollections(page.getContent());
		return new DigitalAssetListResponse(page.getContent(), criteria.getOffset(), criteria.getLimit(), page.getTotalElements());
	}

	@Override
	@Transactional
	public List<Product> addDigitalAssets(List<ProductRequest> requests) {
		List<Product> products = requests.stream().map(request -> {
			// field names in ProductRequest match Product; uuid and audit fields are set on persist
			Product product = new Product();
			BeanUtils.copyProperties(request, product);
			return product;
		}).toList();
		return productRepo.saveAll(products);
	}

	@Override
	@Transactional(readOnly = true)
	public Product getDigitalAsset(String uuid) {
		Product product = findOrThrow(uuid);
		loadCollections(List.of(product));
		return product;
	}

	@Override
	@Transactional
	public void deleteDigitalAsset(String uuid) {
		Product product = findOrThrow(uuid);
		product.setActive(Boolean.FALSE);
		productRepo.save(product);
	}

	// open-in-view is off, so the lazy collections must be loaded while the transaction is still open;
	// with hibernate.default_batch_fetch_size each collection loads for many assets in one query
	private static void loadCollections(Collection<Product> products) {
		for (Product product : products) {
			Hibernate.initialize(product.getLanguage());
			Hibernate.initialize(product.getProductRegion());
			Hibernate.initialize(product.getChannelName());
			Hibernate.initialize(product.getProductBrandID());
			Hibernate.initialize(product.getProductSubBrand());
			Hibernate.initialize(product.getProductLine());
			Hibernate.initialize(product.getProductCategory());
			Hibernate.initialize(product.getProductType());
		}
	}

	private Product findOrThrow(String uuid) {
		return productRepo.findById(uuid)
				.orElseThrow(() -> new ResourceNotFoundException("digitalAsset not found: " + uuid));
	}

}
