package com.prgate.ai.application.port.out;

/** Provider-neutral, safe failure data. Never retain response bodies or credentials. */
public final class AiGenerationException extends RuntimeException {
    public enum Provider { OPENAI, GEMINI }
    public enum Reason {
        AUTHENTICATION_FAILED("El proveedor rechazo la clave de IA configurada."),
        PERMISSION_DENIED("El proyecto no tiene permisos para esta solicitud de IA."),
        QUOTA_EXCEEDED("El proveedor indica que se agoto una cuota o limite del proyecto; revisa su disponibilidad."),
        MODEL_UNAVAILABLE("El modelo no existe o no esta disponible para este proyecto."),
        REQUEST_REJECTED("El proveedor rechazo los parametros de generacion."),
        CONNECTION_FAILED("No se pudo conectar con el proveedor o se agoto el tiempo de espera."),
        RESPONSE_INCOMPLETE("El proveedor no completo la generacion."),
        RESPONSE_BLOCKED("El proveedor bloqueo la generacion de contenido."),
        RESPONSE_INVALID("La respuesta de IA no cumple el formato o las referencias esperadas."),
        UNAVAILABLE("El proveedor de IA no esta disponible.");
        private final String detail;
        Reason(String detail) { this.detail = detail; }
        public String detail() { return detail; }
    }
    private final Provider provider;
    private final Reason reason;
    private final Integer upstreamStatus;
    public AiGenerationException(Provider provider, Reason reason) { this(provider, reason, null); }
    public AiGenerationException(Provider provider, Reason reason, Integer upstreamStatus) {
        super(reason.detail());
        this.provider = java.util.Objects.requireNonNull(provider);
        this.reason = java.util.Objects.requireNonNull(reason);
        this.upstreamStatus = upstreamStatus;
    }
    public Provider provider() { return provider; }
    public Reason reason() { return reason; }
    public Integer upstreamStatus() { return upstreamStatus; }
    public String code() { return provider.name() + "_" + reason.name(); }
}
