package com.odm.dto;

import java.util.List;

import com.odm.entity.Product;

// One entry per requested key, in request order; items is empty when nothing matched
public record AssetLookupResult(AssetLookupKey key, boolean found, List<Product> items) {
}
