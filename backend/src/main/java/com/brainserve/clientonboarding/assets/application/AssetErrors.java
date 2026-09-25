package com.brainserve.clientonboarding.assets.application;

import com.brainserve.clientonboarding.common.error.DomainException;
import org.springframework.http.HttpStatus;

public final class AssetErrors {
    private AssetErrors() { }
    public static DomainException missing() { return new DomainException("RESOURCE_NOT_FOUND", "Requested resource was not found.", HttpStatus.NOT_FOUND); }
    public static DomainException invalid(String message) { return new DomainException("ASSET_VALIDATION_FAILED", message, HttpStatus.BAD_REQUEST); }
    public static DomainException state(String message) { return new DomainException("ASSET_STATE_CONFLICT", message, HttpStatus.CONFLICT); }
    public static DomainException conflict() { return new DomainException("OPTIMISTIC_LOCK_CONFLICT", "This asset changed. Reload before trying again.", HttpStatus.CONFLICT); }
    public static DomainException unavailable() { return new DomainException("ASSET_STORAGE_UNAVAILABLE", "File storage is unavailable. Please try again or contact your project team.", HttpStatus.SERVICE_UNAVAILABLE); }
    public static void page(int page, int size) { if(page < 0 || size < 1 || size > 50) throw invalid("Use a non-negative page and a size between 1 and 50."); }
}
