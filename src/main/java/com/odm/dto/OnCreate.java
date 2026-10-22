package com.odm.dto;

import jakarta.validation.groups.Default;

// Validation group for constraints that apply only when creating an asset (POST), on top of the default ones
public interface OnCreate extends Default {
}
