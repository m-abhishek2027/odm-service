package com.odm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.transaction.CannotCreateTransactionException;

import com.odm.dto.AssetLookupKey;
import com.odm.dto.AssetLookupResult;
import com.odm.dto.DigitalAssetListResponse;
import com.odm.dto.ProductRequest;
import com.odm.entity.ColorSpace;
import com.odm.entity.Product;
import com.odm.exception.ResourceNotFoundException;
import com.odm.service.DigitalAssetMetadataService;

@WebMvcTest(DigitalAssetMetadataController.class)
class DigitalAssetMetadataControllerTest {

	private static final String BASE = "/api/v1/digitalAsset";
	private static final String UUID = "8f3c2a1e-0000-4000-8000-000000000001";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private DigitalAssetMetadataService digitalAssetMetadataService;

	private static Product product(String title) {
		Product product = new Product();
		product.setUuid(UUID);
		product.setTitle(title);
		product.setActive(true);
		return product;
	}

	// ==================== GET /digitalAsset ====================

	@Test
	void getDigitalAssetsBindsAllQueryParameters() throws Exception {
		when(digitalAssetMetadataService.getDigitalAssets(any()))
				.thenReturn(new DigitalAssetListResponse(List.of(product("Bosch")), 10, 5, 11));

		mockMvc.perform(get(BASE)
				.param("offset", "10").param("limit", "5").param("active", "true")
				.param("createdOnFrom", "2026-01-01T00:00:00").param("createdOnTo", "2026-12-31T23:59:59")
				.param("modifiedOnFrom", "2026-02-01T00:00:00").param("modifiedOnTo", "2026-11-30T00:00:00")
				.param("createdBy", "sharan").param("modifiedBy", "abhishek")
				.param("assetName", "brake").param("assetUrl", "cdn").param("assetSubCategory", "Hero"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].uuid").value(UUID))
				.andExpect(jsonPath("$.offset").value(10))
				.andExpect(jsonPath("$.limit").value(5))
				.andExpect(jsonPath("$.totalCount").value(11));

		verify(digitalAssetMetadataService).getDigitalAssets(argThat(c ->
				c.getOffset() == 10 && c.getLimit() == 5 && Boolean.TRUE.equals(c.getActive())
				&& LocalDateTime.of(2026, 1, 1, 0, 0).equals(c.getCreatedOnFrom())
				&& LocalDateTime.of(2026, 12, 31, 23, 59, 59).equals(c.getCreatedOnTo())
				&& LocalDateTime.of(2026, 2, 1, 0, 0).equals(c.getModifiedOnFrom())
				&& LocalDateTime.of(2026, 11, 30, 0, 0).equals(c.getModifiedOnTo())
				&& "sharan".equals(c.getCreatedBy()) && "abhishek".equals(c.getModifiedBy())
				&& "brake".equals(c.getAssetName()) && "cdn".equals(c.getAssetUrl())
				&& "Hero".equals(c.getAssetSubCategory())));
	}

	@Test
	void getDigitalAssetsUsesDefaultOffsetAndLimit() throws Exception {
		when(digitalAssetMetadataService.getDigitalAssets(any()))
				.thenReturn(new DigitalAssetListResponse(List.of(), 0, 20, 0));

		mockMvc.perform(get(BASE)).andExpect(status().isOk());

		verify(digitalAssetMetadataService).getDigitalAssets(argThat(c -> c.getOffset() == 0 && c.getLimit() == 20));
	}

	@Test
	void getDigitalAssetsRejectsInvalidPaging() throws Exception {
		mockMvc.perform(get(BASE).param("offset", "-1").param("limit", "0"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.offset").exists())
				.andExpect(jsonPath("$.errors.limit").exists());

		verify(digitalAssetMetadataService, never()).getDigitalAssets(any());
	}

	@Test
	void getDigitalAssetsRejectsInvalidDate() throws Exception {
		mockMvc.perform(get(BASE).param("createdOnFrom", "yesterday"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.createdOnFrom").exists());
	}

	// ==================== POST /digitalAsset ====================

	@Test
	void addDigitalAssetsAcceptsArray() throws Exception {
		when(digitalAssetMetadataService.addDigitalAssets(anyList()))
				.thenReturn(List.of(product("A"), product("B")));

		mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
				.content("""
						[{"title": "A", "language": ["en"], "source": "External", "supplier": "AutoComp",
						  "brand": "Bosch", "assetName": "a.jpg"},
						 {"title": "B", "dpi": 300, "source": "External", "supplier": "AutoComp",
						  "brand": "Bosch", "assetName": "b.jpg"}]
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[1].title").value("B"));

		verify(digitalAssetMetadataService).addDigitalAssets(argThat(list -> list.size() == 2
				&& "A".equals(list.get(0).getTitle()) && list.get(0).getLanguage().contains("en")
				&& list.get(1).getDpi() == 300.0));
	}

	@Test
	void addDigitalAssetsReportsInvalidElementByIndex() throws Exception {
		mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
				.content("""
						[{"source": "External", "supplier": "AutoComp", "brand": "Bosch", "assetName": "a.jpg"},
						 {"dpi": -1, "fileSource": "External", "productBrandID": ["Bosch"]}]
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors['[1].source']").value("source is required"))
				.andExpect(jsonPath("$.errors['[1].supplier']").value("supplier is required"))
				.andExpect(jsonPath("$.errors['[1].brand']").value("brand is required"))
				.andExpect(jsonPath("$.errors['[1].assetName']").value("assetName is required"))
				.andExpect(jsonPath("$.errors['[1].dpi']").exists())
				.andExpect(jsonPath("$.errors['[0].source']").doesNotExist());

		verify(digitalAssetMetadataService, never()).addDigitalAssets(anyList());
	}

	@Test
	void addDigitalAssetsRejectsBlankRequiredValues() throws Exception {
		mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
				.content("""
						[{"source": " ", "supplier": "", "brand": "  ", "assetName": "a.jpg"}]
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors['[0].source']").exists())
				.andExpect(jsonPath("$.errors['[0].supplier']").exists())
				.andExpect(jsonPath("$.errors['[0].brand']").exists())
				.andExpect(jsonPath("$.errors['[0].assetName']").doesNotExist());

		verify(digitalAssetMetadataService, never()).addDigitalAssets(anyList());
	}

	@Test
	void addDigitalAssetsNeedsOnlyTheFourRequiredFields() throws Exception {
		when(digitalAssetMetadataService.addDigitalAssets(anyList())).thenReturn(List.of(product(null)));

		mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
				.content("""
						[{"source": "External", "supplier": "AutoComp", "brand": "Bosch", "assetName": "a.jpg"}]
						"""))
				.andExpect(status().isCreated());

		verify(digitalAssetMetadataService).addDigitalAssets(argThat(list -> {
			ProductRequest r = list.get(0);
			return "External".equals(r.getSource()) && "AutoComp".equals(r.getSupplier())
					&& "Bosch".equals(r.getBrand()) && "a.jpg".equals(r.getAssetName())
					&& r.getFileSource() == null && r.getProductBrandID() == null;
		}));
	}

	@Test
	void addDigitalAssetsRejectsEmptyArray() throws Exception {
		mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("[]"))
				.andExpect(status().isBadRequest());

		verify(digitalAssetMetadataService, never()).addDigitalAssets(anyList());
	}

	@Test
	void addDigitalAssetsRejectsSingleObjectBody() throws Exception {
		mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{\"title\": \"A\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.body").value("invalid type, expected array"));
	}

	@Test
	void addDigitalAssetsReportsWrongFieldType() throws Exception {
		mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
				.content("[{\"source\": \"s\", \"supplier\": \"s\", \"brand\": \"b\", \"assetName\": \"a\", \"dpi\": \"high\"}]"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors['[0].dpi']").value("invalid type, expected number"));
	}

	@Test
	void missingBodyReturnsBadRequest() throws Exception {
		mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("Request body is missing"));
	}

	@Test
	void wrongContentTypeReturns415() throws Exception {
		mockMvc.perform(post(BASE + "/lookup").contentType(MediaType.TEXT_PLAIN).content("x"))
				.andExpect(status().isUnsupportedMediaType());
	}

	// ==================== POST /digitalAsset/lookup ====================

	private static final String KEY_JSON =
			"{\"source\": \"External\", \"supplier\": \"AutoComp\", \"brand\": \"Bosch\", \"origAssetName\": \"BC905.tif\"}";

	@Test
	void lookupReturnsResultPerKey() throws Exception {
		AssetLookupKey found = new AssetLookupKey("External", "AutoComp", "Bosch", "BC905.tif");
		AssetLookupKey missing = new AssetLookupKey("External", "AutoComp", "Bosch", "nope.tif");
		when(digitalAssetMetadataService.lookupDigitalAssets(anyList())).thenReturn(List.of(
				new AssetLookupResult(found, true, List.of(product("Bosch"))),
				new AssetLookupResult(missing, false, List.of())));

		mockMvc.perform(post(BASE + "/lookup").contentType(MediaType.APPLICATION_JSON)
				.content("[" + KEY_JSON + ", {\"source\": \"External\", \"supplier\": \"AutoComp\", \"brand\": \"Bosch\", \"origAssetName\": \"nope.tif\"}]"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].found").value(true))
				.andExpect(jsonPath("$[0].key.origAssetName").value("BC905.tif"))
				.andExpect(jsonPath("$[0].items[0].uuid").value(UUID))
				.andExpect(jsonPath("$[1].found").value(false))
				.andExpect(jsonPath("$[1].items.length()").value(0));

		verify(digitalAssetMetadataService).lookupDigitalAssets(argThat(keys -> keys.size() == 2
				&& "BC905.tif".equals(keys.get(0).getOrigAssetName()) && "nope.tif".equals(keys.get(1).getOrigAssetName())));
	}

	@Test
	void lookupRequiresAllFourFieldsPerKey() throws Exception {
		mockMvc.perform(post(BASE + "/lookup").contentType(MediaType.APPLICATION_JSON)
				.content("[" + KEY_JSON + ", {\"source\": \" \", \"brand\": \"Bosch\"}]"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors['[1].source']").value("source is required"))
				.andExpect(jsonPath("$.errors['[1].supplier']").value("supplier is required"))
				.andExpect(jsonPath("$.errors['[1].origAssetName']").value("origAssetName is required"))
				.andExpect(jsonPath("$.errors['[1].brand']").doesNotExist())
				.andExpect(jsonPath("$.errors['[0].source']").doesNotExist());

		verify(digitalAssetMetadataService, never()).lookupDigitalAssets(anyList());
	}

	@Test
	void lookupRejectsEmptyArray() throws Exception {
		mockMvc.perform(post(BASE + "/lookup").contentType(MediaType.APPLICATION_JSON).content("[]"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.keys").exists());
	}

	@Test
	void lookupRejectsMoreThan500Keys() throws Exception {
		String body = "[" + String.join(",", java.util.Collections.nCopies(501, KEY_JSON)) + "]";

		mockMvc.perform(post(BASE + "/lookup").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.keys").exists());

		verify(digitalAssetMetadataService, never()).lookupDigitalAssets(anyList());
	}

	@Test
	void lookupRejectsNullElement() throws Exception {
		mockMvc.perform(post(BASE + "/lookup").contentType(MediaType.APPLICATION_JSON).content("[" + KEY_JSON + ", null]"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors['[1]']").exists());
	}

	@Test
	void lookupRejectsSingleObjectBody() throws Exception {
		mockMvc.perform(post(BASE + "/lookup").contentType(MediaType.APPLICATION_JSON).content(KEY_JSON))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.body").value("invalid type, expected array"));
	}

	@Test
	void lookupReturns503WhenDatabaseUnavailable() throws Exception {
		when(digitalAssetMetadataService.lookupDigitalAssets(anyList()))
				.thenThrow(new CannotCreateTransactionException("no connection"));

		mockMvc.perform(post(BASE + "/lookup").contentType(MediaType.APPLICATION_JSON).content("[" + KEY_JSON + "]"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.detail").value("Database is temporarily unavailable, please retry"));
	}

	@Test
	void lookupReturns503OnQueryTimeout() throws Exception {
		when(digitalAssetMetadataService.lookupDigitalAssets(anyList()))
				.thenThrow(new QueryTimeoutException("timeout"));

		mockMvc.perform(post(BASE + "/lookup").contentType(MediaType.APPLICATION_JSON).content("[" + KEY_JSON + "]"))
				.andExpect(status().isServiceUnavailable());
	}

	@Test
	void otherDatabaseErrorReturns500WithoutDetails() throws Exception {
		when(digitalAssetMetadataService.lookupDigitalAssets(anyList()))
				.thenThrow(new InvalidDataAccessResourceUsageException("bad SQL: secret table"));

		mockMvc.perform(post(BASE + "/lookup").contentType(MediaType.APPLICATION_JSON).content("[" + KEY_JSON + "]"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.detail").value("Database error"));
	}

	@Test
	void dataIntegrityViolationReturns409() throws Exception {
		when(digitalAssetMetadataService.addDigitalAssets(anyList()))
				.thenThrow(new DataIntegrityViolationException("duplicate"));

		mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
				.content("[{\"source\": \"s\", \"supplier\": \"s\", \"brand\": \"b\", \"assetName\": \"a\"}]"))
				.andExpect(status().isConflict());
	}

	@Test
	void getDigitalAssetsBindsBusinessKeyFilters() throws Exception {
		when(digitalAssetMetadataService.getDigitalAssets(any()))
				.thenReturn(new DigitalAssetListResponse(List.of(), 0, 20, 0));

		mockMvc.perform(get(BASE).param("source", "External").param("supplier", "AutoComp")
				.param("brand", "Bosch").param("origAssetName", "BC905.tif"))
				.andExpect(status().isOk());

		verify(digitalAssetMetadataService).getDigitalAssets(argThat(c -> "External".equals(c.getSource())
				&& "AutoComp".equals(c.getSupplier()) && "Bosch".equals(c.getBrand())
				&& "BC905.tif".equals(c.getOrigAssetName())));
	}

	// ==================== GET /digitalAsset/{uuid} ====================

	@Test
	void getDigitalAssetReturnsAsset() throws Exception {
		when(digitalAssetMetadataService.getDigitalAsset(UUID)).thenReturn(product("Bosch"));

		mockMvc.perform(get(BASE + "/" + UUID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.uuid").value(UUID))
				.andExpect(jsonPath("$.active").value(true));
	}

	@Test
	void getDigitalAssetReturns404WhenMissing() throws Exception {
		when(digitalAssetMetadataService.getDigitalAsset("nope"))
				.thenThrow(new ResourceNotFoundException("digitalAsset not found: nope"));

		mockMvc.perform(get(BASE + "/nope"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.detail").value("digitalAsset not found: nope"));
	}

	// ==================== PUT /digitalAsset/{uuid} ====================

	@Test
	void updateDigitalAssetReturnsUpdatedObject() throws Exception {
		when(digitalAssetMetadataService.updateDigitalAsset(eq(UUID), any(ProductRequest.class)))
				.thenReturn(product("Updated"));

		mockMvc.perform(put(BASE + "/" + UUID).contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\": \"Updated\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("Updated"));
	}

	@Test
	void updateDigitalAssetValidatesBody() throws Exception {
		mockMvc.perform(put(BASE + "/" + UUID).contentType(MediaType.APPLICATION_JSON).content("{\"dpi\": -1}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.dpi").exists())
				.andExpect(jsonPath("$.errors.source").doesNotExist())
				.andExpect(jsonPath("$.errors.supplier").doesNotExist())
				.andExpect(jsonPath("$.errors.brand").doesNotExist())
				.andExpect(jsonPath("$.errors.assetName").doesNotExist());
	}

	@Test
	void updateDigitalAssetAcceptsColorSpaceEnum() throws Exception {
		Product updated = product("Bosch");
		updated.setColorSpace(ColorSpace.CMY);
		when(digitalAssetMetadataService.updateDigitalAsset(eq(UUID), any(ProductRequest.class))).thenReturn(updated);

		mockMvc.perform(put(BASE + "/" + UUID).contentType(MediaType.APPLICATION_JSON).content("{\"colorSpace\": \"CMY\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.colorSpace").value("CMY"));

		verify(digitalAssetMetadataService).updateDigitalAsset(eq(UUID), argThat(r -> r.getColorSpace() == ColorSpace.CMY));
	}

	@Test
	void updateDigitalAssetRejectsUnknownColorSpace() throws Exception {
		mockMvc.perform(put(BASE + "/" + UUID).contentType(MediaType.APPLICATION_JSON).content("{\"colorSpace\": \"sRGB\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.colorSpace")
						.value("invalid value 'sRGB', allowed values: RGB, CMY, BIT, GRA, VEC, OTH, WEB"));

		verify(digitalAssetMetadataService, never()).updateDigitalAsset(any(), any());
	}

	@Test
	void addDigitalAssetsReportsUnknownColorSpaceByIndex() throws Exception {
		mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
				.content("""
						[{"source": "External", "supplier": "AutoComp", "brand": "Bosch", "assetName": "a.jpg", "colorSpace": "RGB"},
						 {"source": "External", "supplier": "AutoComp", "brand": "Bosch", "assetName": "b.jpg", "colorSpace": "rgb"}]
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors['[1].colorSpace']").exists());

		verify(digitalAssetMetadataService, never()).addDigitalAssets(anyList());
	}

	@Test
	void updateUsesPathUuidAndIgnoresUuidInBody() throws Exception {
		when(digitalAssetMetadataService.updateDigitalAsset(eq(UUID), any(ProductRequest.class)))
				.thenReturn(product("Bosch"));

		mockMvc.perform(put(BASE + "/" + UUID).contentType(MediaType.APPLICATION_JSON)
				.content("{\"uuid\": \"hacked-uuid\", \"createdBy\": \"x\", \"createdOn\": \"2000-01-01T00:00:00\", \"rights\": \"Licensed\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.uuid").value(UUID));

		verify(digitalAssetMetadataService).updateDigitalAsset(eq(UUID), argThat(r -> "Licensed".equals(r.getRights())));
		verify(digitalAssetMetadataService, never()).updateDigitalAsset(eq("hacked-uuid"), any());
	}

	@Test
	void updateDigitalAssetAllowsBlankTitle() throws Exception {
		when(digitalAssetMetadataService.updateDigitalAsset(eq(UUID), any(ProductRequest.class)))
				.thenReturn(product("Bosch"));

		mockMvc.perform(put(BASE + "/" + UUID).contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\": \"\", \"mfg\": null, \"assetName\": \"x.jpg\"}"))
				.andExpect(status().isOk());

		verify(digitalAssetMetadataService).updateDigitalAsset(eq(UUID), argThat(r -> "x.jpg".equals(r.getAssetName())));
	}

	@Test
	void updateDigitalAssetReturns404WhenMissing() throws Exception {
		when(digitalAssetMetadataService.updateDigitalAsset(eq("nope"), any()))
				.thenThrow(new ResourceNotFoundException("digitalAsset not found: nope"));

		mockMvc.perform(put(BASE + "/nope").contentType(MediaType.APPLICATION_JSON).content("{\"title\": \"x\"}"))
				.andExpect(status().isNotFound());
	}

	@Test
	void unexpectedErrorReturns500() throws Exception {
		when(digitalAssetMetadataService.updateDigitalAsset(eq(UUID), any()))
				.thenThrow(new IllegalStateException("db down"));

		mockMvc.perform(put(BASE + "/" + UUID).contentType(MediaType.APPLICATION_JSON).content("{\"title\": \"x\"}"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.detail").value("Internal server error"));
	}

	// ==================== DELETE /digitalAsset/{uuid} ====================

	@Test
	void deleteDigitalAssetReturnsNoContent() throws Exception {
		mockMvc.perform(delete(BASE + "/" + UUID)).andExpect(status().isNoContent());

		verify(digitalAssetMetadataService).deleteDigitalAsset(UUID);
	}

	@Test
	void deleteDigitalAssetReturns404WhenMissing() throws Exception {
		doThrow(new ResourceNotFoundException("digitalAsset not found: nope"))
				.when(digitalAssetMetadataService).deleteDigitalAsset("nope");

		mockMvc.perform(delete(BASE + "/nope")).andExpect(status().isNotFound());
	}

	@Test
	void unsupportedMethodKeepsFrameworkStatus() throws Exception {
		mockMvc.perform(delete(BASE)).andExpect(status().isMethodNotAllowed());
	}

}
