package com.odm.repo;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.odm.entity.Product;

public interface ProductRepository extends JpaRepository<Product, String>, JpaSpecificationExecutor<Product>{

	interface StoredObject {
		String getAssetUrl();

		String getContentMd5();
	}

	// includes soft-deleted assets so a re-import never recreates them; LIKE wildcards in the pattern only widen the match
	@Query("select p.assetUrl as assetUrl, p.contentMd5 as contentMd5 from Product p where p.assetUrl like :pattern")
	List<StoredObject> findStoredObjectsLike(@Param("pattern") String pattern);

	// lookup: active and soft-deleted assets alike, classified by the caller
	List<Product> findByLookupKeyHashIn(Collection<String> lookupKeyHashes);

}
