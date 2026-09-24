package com.shale.ui.component;

/** Result accumulated by a definition-management session. */
public record DefinitionManagementResult(boolean changed) {
    public static final DefinitionManagementResult UNCHANGED = new DefinitionManagementResult(false);
}
