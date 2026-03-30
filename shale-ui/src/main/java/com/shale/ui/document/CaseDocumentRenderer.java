package com.shale.ui.document;

import java.time.LocalDateTime;

/**
 * Backward-compatible wrapper. Prefer {@link CaseDocumentHtmlRenderer}.
 */
public final class CaseDocumentRenderer {
    private final CaseDocumentHtmlRenderer delegate = new CaseDocumentHtmlRenderer();

    public String render(CaseDocumentModel model, CaseDocumentType type, LocalDateTime generatedAt) {
        return delegate.render(model, type, generatedAt);
    }
}
