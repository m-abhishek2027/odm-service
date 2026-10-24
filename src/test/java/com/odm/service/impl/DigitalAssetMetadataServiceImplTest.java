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
