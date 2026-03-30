package com.shale.ui.document;

import java.nio.file.Path;

public record GeneratedDocument(
        Path path,
        String fileName,
        CaseDocumentFormat format
) {
}
