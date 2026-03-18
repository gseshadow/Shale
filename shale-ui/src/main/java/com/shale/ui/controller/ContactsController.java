package com.shale.ui.controller;

import com.shale.data.dao.ContactDao;
import com.shale.data.dao.ContactDao.DirectoryContactRow;
import com.shale.ui.component.factory.ContactCardFactory;
import com.shale.ui.component.factory.ContactCardFactory.ContactCardModel;
import com.shale.ui.state.AppState;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class ContactsController {

    private static final ContactCardFactory.Variant CONTACTS_CARD_VARIANT = ContactCardFactory.Variant.FULL;
    private static final double CONTACT_CARD_WIDTH = 340;
    private static final double CONTACT_CARD_HEIGHT = 78;

    @FXML
    private TextField contactsSearchField;
    @FXML
    private ScrollPane contactsScroll;
    @FXML
    private FlowPane contactsFlow;
    @FXML
    private Label contactsEmptyStateLabel;

    private AppState appState;
    private ContactDao contactDao;
    private ContactCardFactory contactCardFactory;
    private List<DirectoryContactRow> loadedContacts = List.of();
    private String emptyStateMessage = "No contacts to display yet.";
    private int loadGeneration = 0;

    private final ExecutorService dbExec = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "contacts-directory-loader");
        t.setDaemon(true);
        return t;
    });

    public void init(AppState appState, ContactDao contactDao, Consumer<Integer> onOpenContact) {
        this.appState = appState;
        this.contactDao = contactDao;
        this.contactCardFactory = new ContactCardFactory(onOpenContact == null ? id -> {
        } : onOpenContact);
    }

    @FXML
    private void initialize() {
        if (contactsSearchField != null) {
            contactsSearchField.textProperty().addListener((obs, oldV, newV) -> rerender());
        }
        if (contactsFlow != null) {
            contactsFlow.setHgap(14);
            contactsFlow.setVgap(14);
            contactsFlow.setPrefWrapLength(1040);
        }

        Platform.runLater(this::loadContacts);
    }

    private void loadContacts() {
        loadGeneration++;
        final int generationAtSubmit = loadGeneration;

        if (contactDao == null) {
            loadedContacts = List.of();
            setEmptyStateMessage("Contacts are unavailable right now.");
            rerender();
            return;
        }

        Integer tenantId = appState == null ? null : appState.getShaleClientId();
        if (tenantId == null || tenantId <= 0) {
            loadedContacts = List.of();
            setEmptyStateMessage("No tenant is selected.");
            rerender();
            return;
        }

        dbExec.submit(() -> {
            try {
                List<DirectoryContactRow> contacts = new ArrayList<>(contactDao.listContactsForTenant(tenantId));
                contacts.sort(Comparator.comparing((DirectoryContactRow row) -> safe(row.displayName()), String.CASE_INSENSITIVE_ORDER)
                        .thenComparingInt(DirectoryContactRow::id));

                Platform.runLater(() -> {
                    if (generationAtSubmit != loadGeneration) {
                        return;
                    }
                    loadedContacts = List.copyOf(contacts);
                    setEmptyStateMessage("No contacts to display yet.");
                    rerender();
                });
            } catch (RuntimeException ex) {
                Platform.runLater(() -> {
                    if (generationAtSubmit != loadGeneration) {
                        return;
                    }
                    loadedContacts = List.of();
                    setEmptyStateMessage("Unable to load contacts.");
                    rerender();
                });
            }
        });
    }

    private void rerender() {
        if (contactsFlow == null) {
            return;
        }

        List<DirectoryContactRow> filteredContacts = loadedContacts.stream()
                .filter(Objects::nonNull)
                .filter(this::matchesSearch)
                .toList();

        List<Node> cards = filteredContacts.stream()
                .map(this::buildCard)
                .toList();

        contactsFlow.getChildren().setAll(cards);

        boolean empty = cards.isEmpty();
        String query = normalizedQuery();
        if (empty && !query.isBlank() && !loadedContacts.isEmpty()) {
            setEmptyStateMessage("No contacts match your search.");
        } else if (empty && loadedContacts.isEmpty()) {
            setEmptyStateMessage(emptyStateMessage);
        }
        updateEmptyState(empty);
    }

    private Node buildCard(DirectoryContactRow row) {
        String displayName = safe(row.displayName()).isBlank() ? "—" : safe(row.displayName());
        var card = contactCardFactory.create(new ContactCardModel(
                row.id(),
                displayName,
                row.email(),
                row.phone()), CONTACTS_CARD_VARIANT);
        card.setMinHeight(CONTACT_CARD_HEIGHT);
        card.setPrefHeight(CONTACT_CARD_HEIGHT);
        card.setPrefWidth(CONTACT_CARD_WIDTH);
        card.setMaxWidth(CONTACT_CARD_WIDTH);
        return card;
    }

    private boolean matchesSearch(DirectoryContactRow row) {
        String query = normalizedQuery();
        if (query.isBlank()) {
            return true;
        }

        String displayName = safe(row.displayName()).toLowerCase(Locale.ROOT);
        String email = safe(row.email()).toLowerCase(Locale.ROOT);
        String phone = safe(row.phone()).toLowerCase(Locale.ROOT);
        return displayName.contains(query) || email.contains(query) || phone.contains(query);
    }

    private void updateEmptyState(boolean empty) {
        if (contactsEmptyStateLabel != null) {
            contactsEmptyStateLabel.setVisible(empty);
            contactsEmptyStateLabel.setManaged(empty);
        }
        if (contactsScroll != null) {
            contactsScroll.setVisible(!empty);
            contactsScroll.setManaged(!empty);
        }
    }

    private void setEmptyStateMessage(String message) {
        emptyStateMessage = safe(message);
        if (contactsEmptyStateLabel != null) {
            contactsEmptyStateLabel.setText(emptyStateMessage);
        }
    }

    private String normalizedQuery() {
        if (contactsSearchField == null || contactsSearchField.getText() == null) {
            return "";
        }
        return contactsSearchField.getText().trim().toLowerCase(Locale.ROOT);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
