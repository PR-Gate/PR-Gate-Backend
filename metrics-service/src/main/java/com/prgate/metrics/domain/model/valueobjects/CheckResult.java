package com.prgate.metrics.domain.model.valueobjects;
import com.prgate.metrics.domain.model.enums.CheckStatus;
public record CheckResult(CheckId checkId, CheckStatus status, String reason) {
    public CheckResult { Checks.required(checkId); Checks.required(status); reason = Checks.text(reason); }
}
