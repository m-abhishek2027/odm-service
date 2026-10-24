package com.odm.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Body of POST /digitalAsset/metadata/batch: {"assets": [{"uuid", "metadataModifiedAt", ...metadata fields}, ...]}
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MetadataBatchUpdateRequest {

	@NotEmpty(message = "assets must contain at least one entry")
	@Size(max = 500, message = "at most 500 assets per request")
	private List<@NotNull(message = "asset entry must not be null") @Valid MetadataUpdate> assets;

}
