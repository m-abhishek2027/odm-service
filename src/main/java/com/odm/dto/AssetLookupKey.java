package com.odm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// One entry of POST /digitalAsset/lookup as sent by AEM.
// referenceId is checked with the request (default group): without it a result cannot be correlated.
// The key fields are checked per item (LookupItemChecks): a bad item becomes INVALID, the batch goes on.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssetLookupKey {

	@NotBlank(message = "referenceId is required")
	@Size(max = 255, message = "referenceId must be at most 255 characters")
	private String referenceId;

	@NotBlank(message = "fileName is required", groups = LookupItemChecks.class)
	@Size(max = 255, message = "fileName must be at most 255 characters", groups = LookupItemChecks.class)
	private String fileName;

	@NotBlank(message = "source is required", groups = LookupItemChecks.class)
	@Size(max = 255, message = "source must be at most 255 characters", groups = LookupItemChecks.class)
	private String source;

	@NotBlank(message = "supplier is required", groups = LookupItemChecks.class)
	@Size(max = 255, message = "supplier must be at most 255 characters", groups = LookupItemChecks.class)
	private String supplier;

	@NotBlank(message = "brand is required", groups = LookupItemChecks.class)
	@Size(max = 255, message = "brand must be at most 255 characters", groups = LookupItemChecks.class)
	private String brand;

}
