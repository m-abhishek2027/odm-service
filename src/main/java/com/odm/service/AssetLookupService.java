package com.odm.service;

import java.util.List;

import com.odm.dto.AssetLookupKey;
import com.odm.dto.AssetLookupResponse;

public interface AssetLookupService {

	AssetLookupResponse lookup(String requestId, List<AssetLookupKey> items);

}
