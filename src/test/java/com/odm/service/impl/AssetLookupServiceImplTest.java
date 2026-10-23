package com.odm.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.odm.dto.AssetLookupKey;
import com.odm.dto.AssetLookupResponse;
import com.odm.dto.LookupResult;
import com.odm.dto.LookupStatus;
import com.odm.entity.Product;
import com.odm.exception.InvalidLookupRequestException;
import com.odm.lookup.LookupKey;
import com.odm.repo.ProductRepository;

import jakarta.validation.Validation;

class AssetLookupServiceImplTest {

	private ProductRepository productRepo;

	private AssetLookupServiceImpl service;

	@BeforeEach
	void setUp() {
		productRepo = mock(ProductRepository.class);
		service = new AssetLookupServiceImpl(productRepo, Validation.buildDefaultValidatorFactory().getValidator());
	}

	private static Product asset(String uuid, String fileName, boolean active, String md5, int createdDay) {
		Product p = new Product();
		p.setUuid(uuid);
		p.setSource("external");
		p.setSupplier("autocomp");
		p.setBrand("bosch");
		p.setAssetName(fileName);
		p.setOrigAssetName(fileName);
		p.setActive(active);
		p.setContentMd5(md5);
		p.setCreatedOn(LocalDateTime.of(2026, 10, createdDay, 0, 0));
		p.setAssetUrl("gs://bucket/products/" + uuid + "/" + fileName);
		p.setLookupKeyHash(LookupKey.hashOf(p.getSource(), p.getSupplier(), p.getBrand(), fileName));
		return p;
	}

	private static AssetLookupKey item(String referenceId, String fileName) {
		return new AssetLookupKey(referenceId, fileName, "external", "autocomp", "bosch");
	}

	private void dbHas(Product... products) {
		when(productRepo.findByLookupKeyHashIn(anyCollection())).thenReturn(List.of(products));
	}

	@Test
	void oneResultPerItemInRequestOrderWithEveryStatus() {
		dbHas(asset("single", "a.jpg", true, "m1", 1),
				asset("copy-1", "b.jpg", true, "same", 5), asset("copy-2", "b.jpg", true, "same", 2),
				asset("diff-1", "c.jpg", true, "x", 1), asset("diff-2", "c.jpg", true, "y", 2),
				asset("diff-3", "c.jpg", true, "z", 3),
				asset("gone", "d.jpg", false, "m4", 1));

		AssetLookupResponse response = service.lookup("req-1", List.of(
				item("r-found", "a.jpg"),
				item("r-copies", "b.jpg"),
				item("r-different", "c.jpg"),
				item("r-deleted", "d.jpg"),
				item("r-missing", "e.jpg"),
				new AssetLookupKey("r-invalid", "f.jpg", "external", " ", "bosch")));

		List<LookupResult> r = response.results();
		assertThat(r).extracting(LookupResult::referenceId)
				.containsExactly("r-found", "r-copies", "r-different", "r-deleted", "r-missing", "r-invalid");
		assertThat(r).extracting(LookupResult::status).containsExactly(LookupStatus.FOUND, LookupStatus.DUPLICATE,
				LookupStatus.DUPLICATE, LookupStatus.DELETED, LookupStatus.NOT_FOUND, LookupStatus.INVALID);
		// retryable = can change without AEM: NOT_FOUND and DUPLICATE
		assertThat(r).extracting(LookupResult::retryable).containsExactly(false, true, true, false, true, false);
		assertThat(r).extracting(LookupResult::matchCount).containsExactly(1, 2, 3, null, null, null);

		assertThat(r.get(0).asset().uuid()).isEqualTo("single");
		assertThat(r.get(0).asset().fileName()).isEqualTo("a.jpg");

		// two or more matches, identical content or not: status and count only, never data
		assertThat(r.get(1).asset()).isNull();
		assertThat(r.get(2).asset()).isNull();

		assertThat(r.get(3).asset()).isNull();
		assertThat(r.get(4).asset()).isNull();
		assertThat(r.get(5).errors()).containsEntry("supplier", "supplier is required");

		assertThat(response.requestId()).isEqualTo("req-1");
		assertThat(response.timestamp()).isNotNull();
		assertThat(response.status()).isEqualTo(AssetLookupResponse.PARTIAL);
		assertThat(response.summary()).isEqualTo(new AssetLookupResponse.Summary(6, 1, 1, 2, 1, 1));
	}

	@Test
	@SuppressWarnings("unchecked")
	void resolvesTheWholeBatchWithOneQueryOnDistinctHashes() {
		dbHas(asset("a", "a.jpg", true, "m", 1));

		service.lookup("r", List.of(item("1", "a.jpg"), item("2", "A.JPG "), item("3", "b.jpg")));

		ArgumentCaptor<Collection<String>> hashes = ArgumentCaptor.forClass(Collection.class);
		verify(productRepo, times(1)).findByLookupKeyHashIn(hashes.capture());
		assertThat(hashes.getValue()).containsExactlyInAnyOrder(
				LookupKey.hashOf("external", "autocomp", "bosch", "a.jpg"),
				LookupKey.hashOf("external", "autocomp", "bosch", "b.jpg"));
	}

	@Test
	void caseAndSpacesDoNotMatterButStoredSpellingIsReturned() {
		Product stored = asset("a", "13cb25186_01_009.jpg", true, "m", 1);
		dbHas(stored);

		AssetLookupResponse response = service.lookup("r",
				List.of(new AssetLookupKey("1", " 13CB25186_01_009.JPG ", "EXTERNAL", "AutoComp", "Bosch")));

		assertThat(response.status()).isEqualTo(AssetLookupResponse.SUCCESS);
		assertThat(response.results().get(0).asset().fileName()).isEqualTo("13cb25186_01_009.jpg");
		assertThat(response.results().get(0).asset().source()).isEqualTo("external");
	}

	@Test
	void accentsMatter() {
		dbHas(asset("a", "cafe.jpg", true, "m", 1));

		AssetLookupResponse response = service.lookup("r", List.of(item("1", "café.jpg")));

		assertThat(response.results().get(0).status()).isEqualTo(LookupStatus.NOT_FOUND);
	}

	@Test
	void allDuplicatesNoneFound() {
		dbHas(asset("a", "x.jpg", true, "same", 1), asset("b", "x.jpg", true, "same", 2));

		AssetLookupResponse response = service.lookup("r", List.of(item("1", "x.jpg")));

		assertThat(response.status()).isEqualTo(AssetLookupResponse.NONE_FOUND);
		assertThat(response.summary().duplicate()).isEqualTo(1);
	}

	@Test
	void deletedCopiesDoNotCountAgainstAnActiveOne() {
		dbHas(asset("live", "x.jpg", true, "m1", 3), asset("old", "x.jpg", false, "m2", 1));

		LookupResult result = service.lookup("r", List.of(item("1", "x.jpg"))).results().get(0);

		assertThat(result.status()).isEqualTo(LookupStatus.FOUND);
		assertThat(result.matchCount()).isEqualTo(1);
		assertThat(result.asset().uuid()).isEqualTo("live");
	}

	@Test
	void twoAemAssetsWithTheSameKeyEachGetTheirOwnResult() {
		dbHas(asset("a", "x.jpg", true, "m", 1));

		AssetLookupResponse response = service.lookup("r", List.of(item("aem-1", "x.jpg"), item("aem-2", "X.JPG")));

		assertThat(response.results()).extracting(LookupResult::referenceId).containsExactly("aem-1", "aem-2");
		assertThat(response.results()).allMatch(res -> res.status() == LookupStatus.FOUND);
		assertThat(response.summary().found()).isEqualTo(2);
	}

	@Test
	void staleStoredHashIsIgnored() {
		Product stale = asset("a", "x.jpg", true, "m", 1);
		stale.setAssetName("renamed.jpg");
		dbHas(stale);

		assertThat(service.lookup("r", List.of(item("1", "x.jpg"))).results().get(0).status())
				.isEqualTo(LookupStatus.NOT_FOUND);
	}

	@Test
	void duplicateReferenceIdRejectsTheRequest() {
		assertThatThrownBy(() -> service.lookup("r", List.of(item("same", "a.jpg"), item("other", "b.jpg"),
				item("same", "c.jpg"))))
				.isInstanceOf(InvalidLookupRequestException.class)
				.satisfies(ex -> assertThat(((InvalidLookupRequestException) ex).getErrors())
						.containsEntry("assets[2].referenceId", "duplicate referenceId, also at assets[0]"));
		verify(productRepo, never()).findByLookupKeyHashIn(anyCollection());
	}

	@Test
	void allInvalidRunsNoQueryAndFindsNothing() {
		AssetLookupResponse response = service.lookup("r",
				List.of(new AssetLookupKey("1", "", "external", "autocomp", "bosch")));

		verify(productRepo, never()).findByLookupKeyHashIn(anyCollection());
		assertThat(response.status()).isEqualTo(AssetLookupResponse.NONE_FOUND);
		assertThat(response.results().get(0).errors()).containsEntry("fileName", "fileName is required");
	}

	@Test
	void overlongFieldIsInvalid() {
		AssetLookupResponse response = service.lookup("r",
				List.of(new AssetLookupKey("1", "a".repeat(256), "external", "autocomp", "bosch")));

		assertThat(response.results().get(0).status()).isEqualTo(LookupStatus.INVALID);
		assertThat(response.results().get(0).errors()).containsKey("fileName");
	}

}
