package com.odm.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.odm.dto.AssetLookupRequest;
import com.odm.dto.AssetLookupResponse;
import com.odm.dto.DigitalAssetListResponse;
import com.odm.dto.DigitalAssetSearchCriteria;
import com.odm.dto.MetadataBatchUpdateRequest;
import com.odm.dto.MetadataUpdate;
import com.odm.dto.MetadataUpdateResponse;
import com.odm.dto.MetadataUpdateResult;
import com.odm.dto.MetadataUpdateStatus;
import com.odm.dto.OnCreate;
import com.odm.dto.ProductRequest;
import com.odm.entity.Product;
import com.odm.service.AssetLookupService;
import com.odm.service.AssetMetadataUpdateService;
import com.odm.service.DigitalAssetMetadataService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import jakarta.validation.groups.ConvertGroup;
import jakarta.validation.groups.Default;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping(value = "/api/v1/digitalAsset", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class DigitalAssetMetadataController {

	static final String REQUEST_ID_HEADER = "X-Request-Id";

	// anything else in the header is replaced by a generated id rather than echoed back
	private static final Pattern REQUEST_ID_PATTERN = Pattern.compile("[A-Za-z0-9._:-]{1,100}");

	private final DigitalAssetMetadataService digitalAssetMetadataService;

	private final AssetLookupService assetLookupService;

	private final AssetMetadataUpdateService metadataUpdateService;

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

	// AEM lookup by (fileName, source, supplier, brand): one result per requested item, in request order,
	// correlated by the item's referenceId. An X-Request-Id header is echoed back as requestId; otherwise one
	// is generated.
	@PostMapping(value = "/lookup", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<AssetLookupResponse> lookupDigitalAssets(
			@RequestHeader(value = REQUEST_ID_HEADER, required = false) String requestId,
			@Valid @RequestBody AssetLookupRequest request){
		return ResponseEntity.ok(assetLookupService.lookup(resolveRequestId(requestId), request.getAssets()));
	}

	private static String resolveRequestId(String requestId) {
		if (requestId != null && REQUEST_ID_PATTERN.matcher(requestId).matches()) {
			return requestId;
		}
		return "odm-" + UUID.randomUUID();
	}

	@GetMapping("/{uuid}")
	public ResponseEntity<Product> getDigitalAsset(@PathVariable String uuid){
		return ResponseEntity.ok(digitalAssetMetadataService.getDigitalAsset(uuid));
	}

	// AEM metadata for one asset: only the 25 metadata fields, applied when metadataModifiedAt is newer than the
	// stored one; missing or blank fields keep their value. 200 for UPDATED / UNCHANGED, otherwise a problem detail.
	@PutMapping(value = "/{uuid}", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Object> updateMetadata(
			@RequestHeader(value = REQUEST_ID_HEADER, required = false) String requestId,
			@PathVariable String uuid, @Valid @RequestBody MetadataUpdate update){
		if (update.getUuid() != null && !update.getUuid().equals(uuid)) {
			return problem(HttpStatus.BAD_REQUEST, "Validation failed", null,
					Map.of("errors", Map.of("uuid", "must match the uuid in the path")));
		}
		update.setUuid(uuid);
		MetadataUpdateResult result = metadataUpdateService.update(resolveRequestId(requestId), List.of(update))
				.results().get(0);
		return switch (result.status()) {
			case UPDATED, UNCHANGED -> ResponseEntity.ok(result);
			case INVALID -> problem(HttpStatus.BAD_REQUEST, "Validation failed", result.status(),
					Map.of("errors", result.errors()));
			case NOT_FOUND -> problem(HttpStatus.NOT_FOUND, "digitalAsset not found: " + uuid, result.status(), Map.of());
			case DELETED -> problem(HttpStatus.CONFLICT, "digitalAsset is deleted: " + uuid, result.status(), Map.of());
			case STALE -> problem(HttpStatus.CONFLICT, "Newer metadata is already stored", result.status(),
					Map.of("storedMetadataModifiedAt", result.storedMetadataModifiedAt()));
		};
	}

	// AEM scheduler: metadata for up to 500 assets, one result per item in request order
	@PostMapping(value = "/metadata/batch", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<MetadataUpdateResponse> updateMetadataBatch(
			@RequestHeader(value = REQUEST_ID_HEADER, required = false) String requestId,
			@Valid @RequestBody MetadataBatchUpdateRequest request){
		return ResponseEntity.ok(metadataUpdateService.update(resolveRequestId(requestId), request.getAssets()));
	}

	private static ResponseEntity<Object> problem(HttpStatus status, String detail, MetadataUpdateStatus updateStatus,
			Map<String, Object> properties) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		if (updateStatus != null) {
			problem.setProperty("updateStatus", updateStatus);
		}
		properties.forEach(problem::setProperty);
		return ResponseEntity.status(status).body(problem);
	}

	// soft delete: sets active = false
	@DeleteMapping("/{uuid}")
	public ResponseEntity<Void> deleteDigitalAsset(@PathVariable String uuid){
		digitalAssetMetadataService.deleteDigitalAsset(uuid);
		return ResponseEntity.noContent().build();
	}


}
