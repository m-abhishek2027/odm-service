package com.odm.service;

import java.util.List;

import com.odm.dto.MetadataUpdate;
import com.odm.dto.MetadataUpdateResponse;

public interface AssetMetadataUpdateService {

	MetadataUpdateResponse update(String requestId, List<MetadataUpdate> items);

}
