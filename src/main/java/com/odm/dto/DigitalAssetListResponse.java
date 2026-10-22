package com.odm.dto;

import java.util.List;

import com.odm.entity.Product;

public record DigitalAssetListResponse(List<Product> items, long offset, int limit, long totalCount) {
}
