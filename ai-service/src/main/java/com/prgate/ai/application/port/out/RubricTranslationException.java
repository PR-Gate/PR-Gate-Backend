package com.prgate.ai.application.port.out;

/** Carries only safe categories, never a provider body, key or raw provider message. */
public class RubricTranslationException extends RuntimeException {
    public enum Reason {
        CREDITS_EXHAUSTED("OPENAI_CREDITS_EXHAUSTED", "OpenAI indica que la organizacion de esta clave no tiene creditos disponibles."),
        USAGE_LIMIT("OPENAI_USAGE_LIMIT", "OpenAI indica que se alcanzo un limite de gasto o uso de la organizacion o proyecto."),
        AUTHENTICATION("OPENAI_AUTHENTICATION_FAILED", "OpenAI rechazo la clave configurada en el servidor."),
        PERMISSION("OPENAI_PERMISSION_DENIED", "La clave o el proyecto no tienen permiso para realizar esta solicitud."),
        MODEL("OPENAI_MODEL_UNAVAILABLE", "El modelo configurado no existe o no esta disponible para este proyecto."),
        RATE_LIMIT("OPENAI_RATE_LIMITED", "OpenAI limito temporalmente la frecuencia de solicitudes."),
        INVALID_REQUEST("OPENAI_REQUEST_REJECTED", "OpenAI rechazo los parametros de generacion; revisar la configuracion del servidor."),
        NETWORK("OPENAI_CONNECTION_FAILED", "No se pudo conectar con OpenAI o se agoto el tiempo de espera."),
        INCOMPLETE("OPENAI_RESPONSE_INCOMPLETE", "OpenAI no completo la generacion del plan."),
        REFUSED("OPENAI_RESPONSE_REFUSED", "OpenAI rechazo generar contenido para esta solicitud."),
        INVALID_RESPONSE("OPENAI_RESPONSE_INVALID", "La respuesta de OpenAI no cumple el formato esperado."),
        GENERAL("AI_GENERATION_FAILED", "No se pudo completar la generacion del plan tecnico.");

        private final String code;
        private final String detail;
        Reason(String code, String detail) { this.code = code; this.detail = detail; }
        public String code() { return code; }
        public String detail() { return detail; }
    }

    private final Reason reason;
    private final Integer upstreamStatus;

    public RubricTranslationException(String message) {
        super(message);
        this.reason = Reason.GENERAL;
        this.upstreamStatus = null;
    }

    public RubricTranslationException(Reason reason) { this(reason, null); }

    public RubricTranslationException(Reason reason, Integer upstreamStatus) {
        super(reason.detail());
        this.reason = reason;
        this.upstreamStatus = upstreamStatus;
    }

    public Reason reason() { return reason; }
    public Integer upstreamStatus() { return upstreamStatus; }

    public static RubricTranslationException fromProvider(int status, String code) {
        Reason reason;
        if ("credit_balance_exhausted".equals(code)) reason = Reason.CREDITS_EXHAUSTED;
        else if ("insufficient_quota".equals(code) || "organization_spend_limit_exceeded".equals(code)
                || "project_spend_limit_exceeded".equals(code) || "organization_usage_limit_exceeded".equals(code))
            reason = Reason.USAGE_LIMIT;
        else if ("model_not_found".equals(code) || status == 404) reason = Reason.MODEL;
        else if (status == 401) reason = Reason.AUTHENTICATION;
        else if (status == 403) reason = Reason.PERMISSION;
        else if (status == 429) reason = Reason.RATE_LIMIT;
        else if (status == 400) reason = Reason.INVALID_REQUEST;
        else reason = Reason.GENERAL;
        return new RubricTranslationException(reason, status);
    }
}