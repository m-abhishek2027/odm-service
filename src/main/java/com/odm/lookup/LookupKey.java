package com.odm.lookup;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;

/**
 * The business key AEM looks assets up by: source, supplier, brand and file name (stored as assetName).
 * Matching is defined here, not by the database collation: each field is Unicode NFC, stripped of leading and
 * trailing whitespace and lower-cased with Locale.ROOT. Case is ignored, accents are not ("café" != "cafe").
 * The SHA-256 of the four normalized fields, joined by U+001F, is stored as products.lookup_key_hash.
 */
public record LookupKey(String source, String supplier, String brand, String fileName) {

	// unit separator: never part of a name, so "ab"+"c" and "a"+"bc" hash differently
	private static final char SEPARATOR = '\u001F';

	/** @return the normalized key, or null when any field is missing or blank */
	public static LookupKey of(String source, String supplier, String brand, String fileName) {
		String s = normalize(source), su = normalize(supplier), b = normalize(brand), f = normalize(fileName);
		if (s == null || su == null || b == null || f == null) {
			return null;
		}
		return new LookupKey(s, su, b, f);
	}

	/** @return the lookup_key_hash for these raw field values, or null when the key is incomplete */
	public static String hashOf(String source, String supplier, String brand, String fileName) {
		LookupKey key = of(source, supplier, brand, fileName);
		return key == null ? null : key.hash();
	}

	static String normalize(String value) {
		if (value == null) {
			return null;
		}
		String normalized = Normalizer.normalize(value, Normalizer.Form.NFC).strip().toLowerCase(Locale.ROOT);
		return normalized.isEmpty() ? null : normalized;
	}

	/** 64 lowercase hex characters */
	public String hash() {
		String joined = source + SEPARATOR + supplier + SEPARATOR + brand + SEPARATOR + fileName;
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(joined.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is not available", ex);
		}
	}

}
