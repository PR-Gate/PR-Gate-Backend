package com.prgate.ai.domain.model.valueobjects;

import java.util.Locale;
import static com.prgate.ai.domain.model.valueobjects.Checks.text;

/** Analyzer language key, such as java or python; never inferred from backend language. */
public record SourceLanguage(String value) {
    public SourceLanguage {
        value = text(value).toLowerCase(Locale.ROOT);
    }
}
