package com.odm.controller;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.odm.db.DatabaseIndexManager;
import com.odm.dto.IndexStatus;

import lombok.RequiredArgsConstructor;

// Indexes are created automatically at startup; these endpoints are for checking and re-running that
@RestController
@RequestMapping(value = "/api/v1/admin/db/indexes", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class DatabaseAdminController {

	private final DatabaseIndexManager databaseIndexManager;

	@GetMapping
	public ResponseEntity<List<IndexStatus>> getIndexStatus(){
		return ResponseEntity.ok(databaseIndexManager.status());
	}

	// creates only the missing indexes from the fixed list; safe to call repeatedly
	@PostMapping
	public ResponseEntity<List<IndexStatus>> ensureIndexes(){
		return ResponseEntity.ok(databaseIndexManager.ensureIndexes());
	}

}
