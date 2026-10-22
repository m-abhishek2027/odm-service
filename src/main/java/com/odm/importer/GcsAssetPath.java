package com.odm.importer;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Asset fields derived from a bucket object path laid out as
 * {@code <prefix>/yyyy/MM/dd/<source>/<supplier>/<brand>/[<assetType>/]...folders.../<file>}.
 * The assetType folder (stills, video, audio, document) is optional; without it the type is
 * inferred from the object's content type. Folders below are not mapped to any field.
 */
public record GcsAssetPath(String objectName, LocalDate folderDate, String source, String supplier, String brand,
		String assetType, String fileName) {

	public static final Set<String> ASSET_TYPES = Set.of("stills", "video", "audio", "document");

	// OS-generated files that are not assets
	private static final Set<String> SYSTEM_FILES = Set.of("thumbs.db", ".ds_store", "desktop.ini");

	public static boolean isSystemFile(String fileName) {
		return SYSTEM_FILES.contains(fileName.toLowerCase(Locale.ROOT));
	}

	/**
	 * @param objectName  full object name, e.g. products/2026/03/16/external/autocomp/bosch/stills/a/b/x.jpg
	 * @param prefix      the listed prefix, e.g. products/ (must be a prefix of objectName)
	 * @param contentType object content type, used only when the path has no assetType folder
	 * @return empty when the path does not follow the layout
	 */
	public static Optional<GcsAssetPath> parse(String objectName, String prefix, String contentType) {
		if (objectName == null || !objectName.startsWith(prefix) || objectName.endsWith("/")) {
			return Optional.empty();
		}
		String[] parts = objectName.substring(prefix.length()).split("/");
		// yyyy, MM, dd, source, supplier, brand, file
		if (parts.length < 7) {
			return Optional.empty();
		}
		for (String part : parts) {
			if (part.isBlank()) {
				return Optional.empty();
			}
		}
		LocalDate date;
		try {
			date = LocalDate.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
		} catch (NumberFormatException | DateTimeException ex) {
			return Optional.empty();
		}
		if (parts[0].length() != 4 || parts[1].length() != 2 || parts[2].length() != 2) {
			return Optional.empty();
		}

		String fileName = parts[parts.length - 1];
		// a folder directly under the brand that names a type wins; otherwise fall back to the content type
		String typeFolder = parts.length > 7 ? parts[6].toLowerCase(Locale.ROOT) : null;
		String assetType = typeFolder != null && ASSET_TYPES.contains(typeFolder)
				? typeFolder
				: assetTypeFromContentType(contentType);

		return Optional.of(new GcsAssetPath(objectName, date, parts[3], parts[4], parts[5], assetType, fileName));
	}

	static String assetTypeFromContentType(String contentType) {
		if (contentType == null) {
			return null;
		}
		String type = contentType.toLowerCase(Locale.ROOT);
		if (type.startsWith("image/")) {
			return "stills";
		}
		if (type.startsWith("video/")) {
			return "video";
		}
		if (type.startsWith("audio/")) {
			return "audio";
		}
		if (type.equals("application/pdf") || type.startsWith("text/") || type.contains("word")
				|| type.contains("excel") || type.contains("spreadsheet") || type.contains("presentation")
				|| type.contains("powerpoint")) {
			return "document";
		}
		return null;
	}

	public String fileNameWithoutExtension() {
		int dot = fileName.lastIndexOf('.');
		return dot > 0 ? fileName.substring(0, dot) : fileName;
	}

}
