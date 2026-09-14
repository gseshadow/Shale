package com.shale.ui.controller;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import com.shale.ui.component.DefinitionManagementResult;
import com.shale.ui.component.DefinitionManagementSession;
import com.shale.ui.component.spellcheck.UserDictionarySession;

import javafx.stage.Window;

/** Construction boundary for the current user's dictionary manager. */
public final class CustomDictionaryManagementLauncher {
    private final UserDictionarySession dictionary;
    private final Executor executor;

    public CustomDictionaryManagementLauncher(UserDictionarySession dictionary, Executor executor) {
        this.dictionary = Objects.requireNonNull(dictionary, "dictionary");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public void open(Window owner, Consumer<DefinitionManagementResult> onClosed) {
        DefinitionManagementSession session = new DefinitionManagementSession();
        CustomDictionaryManagementPane pane = new CustomDictionaryManagementPane(
                dictionary, executor, session.changes());
        session.show(owner, "Manage Custom Dictionary",
                "Add or remove words accepted by Shale’s spelling tools. Your dictionary is private to your signed-in account.",
                pane.node(), () -> !pane.mutationInFlight(), pane::dispose, onClosed);
    }
}
