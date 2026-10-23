package com.odm.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import com.odm.dto.DigitalAssetListResponse;
import com.odm.dto.DigitalAssetSearchCriteria;
import com.odm.dto.ProductRequest;
import com.odm.entity.Product;
import com.odm.exception.ResourceNotFoundException;
import com.odm.repo.ProductRepository;

@ExtendWith(MockitoExtension.class)
class DigitalAssetMetadataServiceImplTest {

	@Mock
	private ProductRepository productRepo;

	@InjectMocks
	private DigitalAssetMetadataServiceImpl service;

	private static Product existing() {
		Product product = new Product();
		product.setUuid("u1");
		product.setTitle("Old");
		product.setMfg("OldMfg");
		product.setActive(true);
		product.setCreatedBy("creator");
		product.setCreatedOn(LocalDateTime.of(2026, 1, 1, 0, 0));
		return product;
	}

	@Test
	@SuppressWarnings("unchecked")
	void getDigitalAssetsUsesRowOffsetNotPageNumber() {
		DigitalAssetSearchCriteria criteria = new DigitalAssetSearchCriteria();
		criteria.setOffset(7);
		criteria.setLimit(5);
		when(productRepo.findAll(any(Specification.class), any(Pageable.class)))
				.thenAnswer(inv -> new PageImpl<>(List.of(existing()), inv.getArgument(1), 8));

		DigitalAssetListResponse response = service.getDigitalAssets(criteria);

		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(productRepo).findAll(any(Specification.class), pageable.capture());
		assertThat(pageable.getValue().getOffset()).isEqualTo(7);
		assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
		assertThat(pageable.getValue().getSort().getOrderFor("createdOn").getDirection()).isEqualTo(Sort.Direction.DESC);
		assertThat(response.items()).hasSize(1);
		assertThat(response.offset()).isEqualTo(7);
		assertThat(response.limit()).isEqualTo(5);
		assertThat(response.totalCount()).isEqualTo(8);
	}

	@Test
	void addDigitalAssetsCopiesEveryRequest() {
		ProductRequest a = new ProductRequest();
		a.setTitle("A");
		a.setAssetName("a.jpg");
		ProductRequest b = new ProductRequest();
		b.setTitle("B");
		b.setProductBrandID(List.of("Bosch"));
		when(productRepo.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

		List<Product> saved = service.addDigitalAssets(List.of(a, b));

		assertThat(saved).extracting(Product::getTitle).containsExactly("A", "B");
		assertThat(saved.get(0).getAssetName()).isEqualTo("a.jpg");
		assertThat(saved.get(1).getProductBrandID()).containsExactly("Bosch");
		assertThat(saved).allSatisfy(p -> assertThat(p.getUuid()).isNull());
	}

	@Test
	void getDigitalAssetThrowsWhenMissing() {
		when(productRepo.findById("x")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getDigitalAsset("x"))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessageContaining("x");
	}

	@Test
	void updateSetsProvidedFieldsAndKeepsOmittedOnes() {
		Product product = existing();
		when(productRepo.findById("u1")).thenReturn(Optional.of(product));
		when(productRepo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
		ProductRequest request = new ProductRequest();
		request.setTitle("New");

		Product updated = service.updateDigitalAsset("u1", request);

		assertThat(updated.getTitle()).isEqualTo("New");
		assertThat(updated.getMfg()).isEqualTo("OldMfg");
		assertThat(updated.getUuid()).isEqualTo("u1");
		assertThat(updated.getCreatedBy()).isEqualTo("creator");
		assertThat(updated.getCreatedOn()).isEqualTo(LocalDateTime.of(2026, 1, 1, 0, 0));
		assertThat(updated.getActive()).isTrue();
	}

	@Test
	void updateIgnoresNullBlankAndEmptyListFields() {
		Product product = existing();
		product.setProductBrandID(List.of("Bosch"));
		product.setDescription("keep me");
		when(productRepo.findById("u1")).thenReturn(Optional.of(product));
		when(productRepo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
		ProductRequest request = new ProductRequest();
		request.setTitle("   ");
		request.setMfg("");
		request.setDescription(null);
		request.setProductBrandID(List.of());
		request.setLanguage(List.of("en", "de"));

		Product updated = service.updateDigitalAsset("u1", request);

		assertThat(updated.getTitle()).isEqualTo("Old");
		assertThat(updated.getMfg()).isEqualTo("OldMfg");
		assertThat(updated.getDescription()).isEqualTo("keep me");
		assertThat(updated.getProductBrandID()).containsExactly("Bosch");
		assertThat(updated.getLanguage()).containsExactly("en", "de");
		assertThat(updated.getActive()).isTrue();
	}

	@Test
	void updateDropsBlankListEntriesAndKeepsListWhenNothingIsLeft() {
		Product product = existing();
		product.setLanguage(List.of("en"));
		product.setProductBrandID(List.of("Bosch"));
		when(productRepo.findById("u1")).thenReturn(Optional.of(product));
		when(productRepo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
		ProductRequest request = new ProductRequest();
		request.setLanguage(java.util.Arrays.asList("", "fr", null, "  ", "de"));
		request.setProductBrandID(java.util.Arrays.asList("", " ", null));

		Product updated = service.updateDigitalAsset("u1", request);

		assertThat(updated.getLanguage()).containsExactly("fr", "de");
		assertThat(updated.getProductBrandID()).containsExactly("Bosch");
	}

	@Test
	void updateNeverChangesUuidOrCreationAudit() {
		Product product = existing();
		when(productRepo.findById("u1")).thenReturn(Optional.of(product));
		when(productRepo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
		ProductRequest request = new ProductRequest();
		request.setTitle("New");

		Product updated = service.updateDigitalAsset("u1", request);

		assertThat(updated.getUuid()).isEqualTo("u1");
		assertThat(updated.getCreatedBy()).isEqualTo("creator");
		assertThat(updated.getCreatedOn()).isEqualTo(LocalDateTime.of(2026, 1, 1, 0, 0));
		// the request type has no way to carry them
		assertThat(org.springframework.beans.BeanUtils.getPropertyDescriptor(ProductRequest.class, "uuid")).isNull();
		assertThat(org.springframework.beans.BeanUtils.getPropertyDescriptor(ProductRequest.class, "createdOn")).isNull();
		assertThat(org.springframework.beans.BeanUtils.getPropertyDescriptor(ProductRequest.class, "createdBy")).isNull();
	}

	@Test
	void updateAppliesFalseAndZeroValues() {
		Product product = existing();
		product.setOkThumb(true);
		product.setDpi(300.0);
		when(productRepo.findById("u1")).thenReturn(Optional.of(product));
		when(productRepo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
		ProductRequest request = new ProductRequest();
		request.setOkThumb(false);
		request.setDpi(0.0);

		Product updated = service.updateDigitalAsset("u1", request);

		assertThat(updated.getOkThumb()).isFalse();
		assertThat(updated.getDpi()).isEqualTo(0.0);
	}

	@Test
	void updateCanReactivateSoftDeletedAsset() {
		Product product = existing();
		product.setActive(false);
		when(productRepo.findById("u1")).thenReturn(Optional.of(product));
		when(productRepo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
		ProductRequest request = new ProductRequest();
		request.setTitle("Old");
		request.setActive(true);

		assertThat(service.updateDigitalAsset("u1", request).getActive()).isTrue();
	}

	@Test
	void updateThrowsWhenMissing() {
		when(productRepo.findById("x")).thenReturn(Optional.empty());
		ProductRequest request = new ProductRequest();
		request.setTitle("t");

		assertThatThrownBy(() -> service.updateDigitalAsset("x", request))
				.isInstanceOf(ResourceNotFoundException.class);
		verify(productRepo, never()).saveAndFlush(any());
	}

	@Test
	void deleteIsSoft() {
		Product product = existing();
		when(productRepo.findById("u1")).thenReturn(Optional.of(product));

		service.deleteDigitalAsset("u1");

		assertThat(product.getActive()).isFalse();
		verify(productRepo).save(product);
		verify(productRepo, never()).delete(any(Product.class));
		verify(productRepo, never()).deleteById(any());
	}

	@Test
	void deleteThrowsWhenMissing() {
		when(productRepo.findById("x")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.deleteDigitalAsset("x"))
				.isInstanceOf(ResourceNotFoundException.class);
	}

}
