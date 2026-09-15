package com.shale.ui.component;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import javafx.scene.Node;
import javafx.stage.Window;

/** Common launch/completion lifecycle for a single definition-management window. */
public final class DefinitionManagementSession {
    private final CommittedChangeTracker changes = new CommittedChangeTracker();

    public CommittedChangeTracker changes() {
        return changes;
    }

    public void show(Window owner, String title, String helpText, Node content,
            BooleanSupplier canClose, Runnable dispose, Consumer<DefinitionManagementResult> onClosed) {
        show(owner, title, helpText, content, DefinitionManagementWindow.ContentMode.SCROLLABLE,
                canClose, dispose, onClosed);
    }

    public void show(Window owner, String title, String helpText, Node content,
            DefinitionManagementWindow.ContentMode contentMode, BooleanSupplier canClose, Runnable dispose,
            Consumer<DefinitionManagementResult> onClosed) {
        DefinitionManagementWindow.show(owner, title, helpText, content, Objects.requireNonNull(contentMode),
                changes::hasCommittedChanges,
                Objects.requireNonNull(canClose), Objects.requireNonNull(dispose), Objects.requireNonNull(onClosed));
    }
}
