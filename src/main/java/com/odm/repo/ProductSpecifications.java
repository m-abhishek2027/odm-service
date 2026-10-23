package com.odm.repo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import com.odm.dto.DigitalAssetSearchCriteria;
import com.odm.entity.Product;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

public final class ProductSpecifications {

	private ProductSpecifications() {
	}

	public static Specification<Product> matching(DigitalAssetSearchCriteria c) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();

			if (c.getActive() != null) {
				predicates.add(cb.equal(root.get("active"), c.getActive()));
			}
			if (c.getCreatedOnFrom() != null) {
				predicates.add(cb.greaterThanOrEqualTo(root.get("createdOn"), c.getCreatedOnFrom()));
			}
			if (c.getCreatedOnTo() != null) {
				predicates.add(cb.lessThanOrEqualTo(root.get("createdOn"), c.getCreatedOnTo()));
			}
			if (c.getModifiedOnFrom() != null) {
				predicates.add(cb.greaterThanOrEqualTo(root.get("modifiedOn"), c.getModifiedOnFrom()));
			}
			if (c.getModifiedOnTo() != null) {
				predicates.add(cb.lessThanOrEqualTo(root.get("modifiedOn"), c.getModifiedOnTo()));
			}
			if (StringUtils.hasText(c.getCreatedBy())) {
				predicates.add(cb.equal(root.get("createdBy"), c.getCreatedBy().trim()));
			}
			if (StringUtils.hasText(c.getModifiedBy())) {
				predicates.add(cb.equal(root.get("modifiedBy"), c.getModifiedBy().trim()));
			}
			if (StringUtils.hasText(c.getAssetName())) {
				predicates.add(containsIgnoreCase(cb, root.get("assetName"), c.getAssetName()));
			}
			if (StringUtils.hasText(c.getAssetUrl())) {
				predicates.add(containsIgnoreCase(cb, root.get("assetUrl"), c.getAssetUrl()));
			}
			if (StringUtils.hasText(c.getAssetSubCategory())) {
				predicates.add(cb.equal(root.get("assetSubCategory"), c.getAssetSubCategory().trim()));
			}
			if (StringUtils.hasText(c.getSource())) {
				predicates.add(cb.equal(root.get("source"), c.getSource().trim()));
			}
			if (StringUtils.hasText(c.getSupplier())) {
				predicates.add(cb.equal(root.get("supplier"), c.getSupplier().trim()));
			}
			if (StringUtils.hasText(c.getBrand())) {
				predicates.add(cb.equal(root.get("brand"), c.getBrand().trim()));
			}
			if (StringUtils.hasText(c.getOrigAssetName())) {
				predicates.add(cb.equal(root.get("origAssetName"), c.getOrigAssetName().trim()));
			}

			return cb.and(predicates.toArray(Predicate[]::new));
		};
	}

	private static Predicate containsIgnoreCase(CriteriaBuilder cb, Expression<String> field, String value) {
		String escaped = value.trim().toLowerCase(Locale.ROOT)
				.replace("\\", "\\\\")
				.replace("%", "\\%")
				.replace("_", "\\_");
		return cb.like(cb.lower(field), "%" + escaped + "%", '\\');
	}

}
