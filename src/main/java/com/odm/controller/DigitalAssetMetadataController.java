package com.odm.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/md")
public class DigitalAssetMetadataController {
	
	@GetMapping("/check")
	public ResponseEntity<String> health(){
		return ResponseEntity.ok("UP");
	}

}
