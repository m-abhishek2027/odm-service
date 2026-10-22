package com.odm.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.odm.entity.Product;

public interface ProductRepository extends JpaRepository<Product, String>, JpaSpecificationExecutor<Product>{

	// includes soft-deleted assets so a re-import never recreates them; LIKE wildcards in the pattern only widen the match
	@Query("select p.assetUrl from Product p where p.assetUrl like :pattern")
	List<String> findAssetUrlsLike(@Param("pattern") String pattern);

}
