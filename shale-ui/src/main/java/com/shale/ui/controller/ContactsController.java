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
import java.util.List;
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
    @FXML
    private Label contactsLoadingStateLabel;

    private AppState appState;
    private ContactDao contactDao;
    private ContactCardFactory contactCardFactory;
    private final List<DirectoryContactRow> loadedContacts = new ArrayList<>();
    private String emptyStateMessage = "No contacts to display yet.";
    private String loadingStateMessage = "Loading contacts…";
    private int loadGeneration = 0;
    private int currentPage = 0;
    private final int pageSize = 100;
    private boolean loading = false;
    private boolean hasMore = true;

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
            contactsSearchField.textProperty().addListener((obs, oldV, newV) -> loadFirstPage());
        }
        if (contactsFlow != null) {
            contactsFlow.setHgap(16);
            contactsFlow.setVgap(16);
            contactsFlow.setPrefWrapLength(1040);
        }

        Platform.runLater(() -> {
            wireInfiniteScroll();
            loadFirstPage();
        });
    }

    private void wireInfiniteScroll() {
        if (contactsScroll == null) {
            return;
        }
        contactsScroll.vvalueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null && newV.doubleValue() >= 0.95) {
                loadNextPage();
            }
        });
    }

    private void loadFirstPage() {
        loadGeneration++;
        currentPage = 0;
        loading = false;
        hasMore = true;
        loadedContacts.clear();
        if (contactsFlow != null) {
            contactsFlow.getChildren().clear();
        }
        loadNextPage();
    }

    private void loadNextPage() {
        if (loading || !hasMore) {
            return;
        }

        final int generationAtSubmit = loadGeneration;

        if (contactDao == null) {
            loadedContacts.clear();
            setEmptyStateMessage("Contacts are unavailable right now.");
            showEmptyState();
            return;
        }

        Integer tenantId = appState == null ? null : appState.getShaleClientId();
        if (tenantId == null || tenantId <= 0) {
            loadedContacts.clear();
            setEmptyStateMessage("No tenant is selected.");
            showEmptyState();
            return;
        }

        loading = true;
        final int pageToLoad = currentPage;
        if (loadedContacts.isEmpty()) {
            setLoadingStateMessage("Loading contacts…");
            updateLoadingState(true);
        } else {
            rerender();
        }

        dbExec.submit(() -> {
            try {
                var page = contactDao.findDirectoryContactsPage(tenantId, pageToLoad, pageSize, normalizedQuery());

                Platform.runLater(() -> {
                    if (generationAtSubmit != loadGeneration) {
                        return;
                    }
                    loadedContacts.addAll(page.items());
                    currentPage++;
                    hasMore = loadedContacts.size() < page.total();
                    loading = false;
                    setEmptyStateMessage("No contacts to display yet.");
                    rerender();
                });
            } catch (RuntimeException ex) {
                Platform.runLater(() -> {
                    if (generationAtSubmit != loadGeneration) {
                        return;
                    }
                    loading = false;
                    loadedContacts.clear();
                    setEmptyStateMessage("Unable to load contacts.");
                    showEmptyState();
                });
            }
        });
    }

    private void rerender() {
        if (contactsFlow == null) {
            return;
        }

        List<Node> cards = loadedContacts.stream()
                .map(this::buildCard)
                .collect(ArrayList::new, List::add, List::addAll);

        if (loading && !loadedContacts.isEmpty()) {
            cards.add(buildLoadingMoreNode());
        }

        contactsFlow.getChildren().setAll(cards);

        boolean empty = loadedContacts.isEmpty();
        String query = normalizedQuery();
        if (empty && !query.isBlank()) {
            setEmptyStateMessage("No contacts match your search.");
        } else if (empty) {
            setEmptyStateMessage(emptyStateMessage);
        }
        updateLoadingState(false);
        updateEmptyState(empty);
    }

    private Node buildCard(DirectoryContactRow row) {
        String displayName = safe(row.displayName()).isBlank() ? "—" : safe(row.displayName());
        var card = contactCardFactory.create(new ContactCardModel(
                row.id(),
                displayName,
                null,
                row.email(),
                row.phone()), CONTACTS_CARD_VARIANT);
        card.setMinHeight(CONTACT_CARD_HEIGHT);
        card.setPrefHeight(CONTACT_CARD_HEIGHT);
        card.setPrefWidth(CONTACT_CARD_WIDTH);
        card.setMaxWidth(CONTACT_CARD_WIDTH);
        return card;
    }

    private Node buildLoadingMoreNode() {
        Label label = new Label("Loading more contacts…");
        label.getStyleClass().add("muted-text");
        label.setMinHeight(CONTACT_CARD_HEIGHT);
        label.setPrefHeight(CONTACT_CARD_HEIGHT);
        label.setPrefWidth(CONTACT_CARD_WIDTH);
        label.setMaxWidth(CONTACT_CARD_WIDTH);
        return label;
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

    private void updateLoadingState(boolean loadingStateVisible) {
        if (contactsLoadingStateLabel != null) {
            contactsLoadingStateLabel.setVisible(loadingStateVisible);
            contactsLoadingStateLabel.setManaged(loadingStateVisible);
        }
        if (loadingStateVisible && contactsEmptyStateLabel != null) {
            contactsEmptyStateLabel.setVisible(false);
            contactsEmptyStateLabel.setManaged(false);
        }
        if (loadingStateVisible && contactsScroll != null) {
            contactsScroll.setVisible(false);
            contactsScroll.setManaged(false);
        }
    }

    private void setEmptyStateMessage(String message) {
        emptyStateMessage = safe(message);
        if (contactsEmptyStateLabel != null) {
            contactsEmptyStateLabel.setText(emptyStateMessage);
        }
    }

    private void setLoadingStateMessage(String message) {
        loadingStateMessage = safe(message);
        if (contactsLoadingStateLabel != null) {
            contactsLoadingStateLabel.setText(loadingStateMessage);
        }
    }

    private void showEmptyState() {
        updateLoadingState(false);
        updateEmptyState(true);
    }

    private String normalizedQuery() {
        if (contactsSearchField == null || contactsSearchField.getText() == null) {
            return "";
        }
        return contactsSearchField.getText().trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
