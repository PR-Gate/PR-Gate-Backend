package com.prgate.ai.application.exception;

public final class CatalogUnavailableException extends RuntimeException {
    public CatalogUnavailableException() { super("Technical rule catalog is unavailable"); }
}
