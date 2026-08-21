package com.brainserve.onboarding.assets.infrastructure.storage;

public class ObjectMissingException extends RuntimeException {
    public ObjectMissingException(String message, Throwable cause) { super(message, cause); }
}
