package com.odm.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.odm.dto.AssetLookupKey;
import com.odm.dto.AssetLookupResult;
import com.odm.dto.DigitalAssetListResponse;
import com.odm.dto.DigitalAssetSearchCriteria;
import com.odm.dto.OnCreate;
import com.odm.dto.ProductRequest;
import com.odm.entity.Product;
import com.odm.service.DigitalAssetMetadataService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.groups.ConvertGroup;
import jakarta.validation.groups.Default;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping(value = "/api/v1/digitalAsset", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class DigitalAssetMetadataController {

	private final DigitalAssetMetadataService digitalAssetMetadataService;

	@GetMapping
	public ResponseEntity<DigitalAssetListResponse> getDigitalAssets(@Valid DigitalAssetSearchCriteria criteria){
		return ResponseEntity.ok(digitalAssetMetadataService.getDigitalAssets(criteria));
	}

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<List<Product>> addDigitalAssets(
			@RequestBody @NotEmpty @Size(max = 500)
			List<@Valid @ConvertGroup(from = Default.class, to = OnCreate.class) ProductRequest> requests){
		return ResponseEntity.status(HttpStatus.CREATED).body(digitalAssetMetadataService.addDigitalAssets(requests));
	}

	// fetch by business key (source, supplier, brand, origAssetName); one key or a batch, one result per key
	@PostMapping(value = "/lookup", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<List<AssetLookupResult>> lookupDigitalAssets(
			@RequestBody @NotEmpty @Size(max = 500) List<@NotNull @Valid AssetLookupKey> keys){
		return ResponseEntity.ok(digitalAssetMetadataService.lookupDigitalAssets(keys));
	}

	@GetMapping("/{uuid}")
	public ResponseEntity<Product> getDigitalAsset(@PathVariable String uuid){
		return ResponseEntity.ok(digitalAssetMetadataService.getDigitalAsset(uuid));
	}

	// partial update: null, blank and empty-list fields in the body are ignored
	@PutMapping(value = "/{uuid}", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Product> updateDigitalAsset(@PathVariable String uuid,
			@Valid @RequestBody ProductRequest request){
		return ResponseEntity.ok(digitalAssetMetadataService.updateDigitalAsset(uuid, request));
	}

	// soft delete: sets active = false
	@DeleteMapping("/{uuid}")
	public ResponseEntity<Void> deleteDigitalAsset(@PathVariable String uuid){
		digitalAssetMetadataService.deleteDigitalAsset(uuid);
		return ResponseEntity.noContent().build();
	}


}
