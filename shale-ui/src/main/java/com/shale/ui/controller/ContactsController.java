package com.shale.ui.controller;

import com.shale.core.service.ContactServicePort;
import com.shale.core.service.ContactServicePort.ContactCardSummary;
import com.shale.ui.component.ScrollableListRegion;
import com.shale.ui.component.factory.ContactCardFactory;
import com.shale.ui.component.factory.ContactCardFactory.ContactCardModel;
import com.shale.ui.state.AppState;
import com.shale.ui.services.LiveUpdateEvents;
import com.shale.ui.services.UiRuntimeBridge;
import com.shale.ui.util.PerfLog;
import com.shale.ui.util.UiStateLabels;
import com.shale.ui.util.ControlStyles;

import javafx.application.Platform;
import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.MenuButton;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Button;
import javafx.scene.layout.FlowPane;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ContactsController {
    private static final Logger LOG = LoggerFactory.getLogger(ContactsController.class);

    private static final ContactCardFactory.Variant CONTACTS_CARD_VARIANT = ContactCardFactory.Variant.FULL;
    private static final double CONTACT_CARD_WIDTH = 340;
    private static final double CONTACT_CARD_HEIGHT = 78;
    private static final Duration SEARCH_DEBOUNCE = Duration.millis(300);

    @FXML
    private TextField contactsSearchField;
    @FXML
    private ScrollableListRegion contactsListRegion;
    @FXML
    private ScrollPane contactsScroll;
    @FXML
    private FlowPane contactsFlow;
    @FXML
    private Label contactsEmptyStateLabel;
    @FXML
    private Label contactsLoadingStateLabel;
    @FXML private MenuButton contactTypeFilter, specialtyFilter, credentialFilter;
    @FXML private FlowPane selectedFilterChips;
    @FXML private Label activeFilterCount;
    @FXML private Button clearFiltersButton;

    private AppState appState;
    private ContactServicePort contactService;
    private UiRuntimeBridge runtimeBridge;
    private ContactCardFactory contactCardFactory;
    private final List<ContactCardSummary> loadedContacts = new ArrayList<>();
    private String emptyStateMessage = "No contacts to display yet.";
    private String loadingStateMessage = "Loading contacts…";
    private PauseTransition searchDebounce;
    private volatile int loadGeneration = 0;
    private volatile String latestRequestedQuery = "";
    private int currentPage = 0;
    private final int pageSize = 100;
    private boolean loading = false;
    private boolean hasMore = true;
    private final Set<Integer> selectedContactTypes=new LinkedHashSet<>(), selectedSpecialties=new LinkedHashSet<>(), selectedCredentials=new LinkedHashSet<>();
    private List<ContactServicePort.Definition> typeOptions=List.of(), specialtyOptions=List.of();
    private List<ContactServicePort.CredentialDefinition> credentialOptions=List.of();
    private long pageLoadStartedNanos;

    private final ExecutorService dbExec = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "contacts-directory-loader");
        t.setDaemon(true);
        return t;
    });

    public void init(AppState appState, ContactServicePort contactService, UiRuntimeBridge runtimeBridge, Consumer<Integer> onOpenContact) {
        this.appState = appState;
        this.contactService = contactService;
        this.runtimeBridge = runtimeBridge;
        if (runtimeBridge != null) runtimeBridge.subscribeEntityUpdated(this::handleContactUpdated);
        this.contactCardFactory = new ContactCardFactory(onOpenContact == null ? id -> {
        } : onOpenContact);
        loadFilterOptions();
    }

    private void loadFilterOptions() {
        Integer tenant=appState==null?null:appState.getShaleClientId();
        if(contactService==null||tenant==null||tenant<=0)return;
        dbExec.submit(()->{ try { var types=contactService.getEffectiveContactTypes(tenant); var specs=contactService.getEffectiveSpecialties(tenant);
            var creds=contactService.getEffectiveCredentialDefinitions(tenant);
            Platform.runLater(()->{typeOptions=types;specialtyOptions=specs;credentialOptions=creds; rebuildFilterMenus();});
        } catch (RuntimeException ex) { LOG.error("Unable to load Contact directory filter definitions for tenant {}", tenant, ex); } });
    }

    private void rebuildFilterMenus(){
        buildDefinitionMenu(contactTypeFilter,typeOptions,selectedContactTypes);
        buildDefinitionMenu(specialtyFilter,specialtyOptions,selectedSpecialties);
        if(credentialFilter!=null){credentialFilter.getItems().clear();for(var d:credentialOptions){var i=new CheckMenuItem(d.abbreviation()+" — "+d.name());i.getStyleClass().add("contact-filter-option");i.setSelected(selectedCredentials.contains(d.id()));i.setOnAction(e->{toggle(selectedCredentials,d.id(),i.isSelected());});credentialFilter.getItems().add(i);}}
        renderFilterState();
    }
    private void buildDefinitionMenu(MenuButton menu,List<ContactServicePort.Definition> options,Set<Integer> selected){if(menu==null)return;menu.getItems().clear();for(var d:options){var i=new CheckMenuItem(d.name());i.getStyleClass().add("contact-filter-option");i.setSelected(selected.contains(d.id()));i.setOnAction(e->toggle(selected,d.id(),i.isSelected()));menu.getItems().add(i);}}
    private void toggle(Set<Integer> selected,int id,boolean on){if(on)selected.add(id);else selected.remove(id);renderFilterState();loadFirstPage();}
    @FXML private void clearFilters(){selectedContactTypes.clear();selectedSpecialties.clear();selectedCredentials.clear();rebuildFilterMenus();loadFirstPage();}
    private void renderFilterState(){int count=selectedContactTypes.size()+selectedSpecialties.size()+selectedCredentials.size();if(activeFilterCount!=null)activeFilterCount.setText(count+" filter"+(count==1?"":"s"));if(clearFiltersButton!=null)clearFiltersButton.setDisable(count==0);if(selectedFilterChips!=null){selectedFilterChips.getChildren().clear();typeOptions.forEach(d->chip(d.id(),d.name(),d.color(),selectedContactTypes));specialtyOptions.forEach(d->chip(d.id(),d.name(),d.color(),selectedSpecialties));credentialOptions.forEach(d->chip(d.id(),d.abbreviation(),d.color(),selectedCredentials));}}
    private void chip(int id,String text,String color,Set<Integer> selected){if(!selected.contains(id))return;Button b=new Button(text+"  ×");b.getStyleClass().add("contact-filter-chip");if(color!=null&&color.matches("#[0-9a-fA-F]{6}"))b.setStyle("-fx-border-color: "+color+"; -fx-background-color: "+color+"22;");b.setOnAction(e->{selected.remove(id);rebuildFilterMenus();loadFirstPage();});selectedFilterChips.getChildren().add(b);}
    private ContactServicePort.DirectoryFilters filters(){return new ContactServicePort.DirectoryFilters(List.copyOf(selectedContactTypes),List.copyOf(selectedSpecialties),List.copyOf(selectedCredentials));}

    @FXML
    private void initialize() {
        if (contactsSearchField != null) {
            ControlStyles.formControl(contactsSearchField);
            searchDebounce = new PauseTransition(SEARCH_DEBOUNCE);
            searchDebounce.setOnFinished(e -> {
                latestRequestedQuery = normalizedQuery();
                PerfLog.log("contacts.search.debounce", "fired", "queryLength=" + latestRequestedQuery.length() + " nextGeneration=" + (loadGeneration + 1));
                loadFirstPage();
            });
            contactsSearchField.textProperty().addListener((obs, oldV, newV) -> scheduleSearchReload());
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
        if (contactsScroll == null && contactsListRegion != null) {
            contactsScroll = contactsListRegion.getScrollPane();
        }
        if (contactsScroll == null) {
            return;
        }
        contactsScroll.vvalueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null && newV.doubleValue() >= 0.95) {
                loadNextPage();
            }
        });
    }

    private void scheduleSearchReload() {
        latestRequestedQuery = normalizedQuery();
        if (searchDebounce == null) {
            loadFirstPage();
            return;
        }
        PerfLog.log("contacts.search.debounce", "scheduled", "queryLength=" + latestRequestedQuery.length() + " delayMs=" + Math.round(SEARCH_DEBOUNCE.toMillis()) + " currentGeneration=" + loadGeneration);
        searchDebounce.playFromStart();
    }

    private void loadFirstPage() {
        long started = PerfLog.start();
        if (searchDebounce != null) {
            searchDebounce.stop();
        }
        latestRequestedQuery = normalizedQuery();
        loadGeneration++;
        currentPage = 0;
        loading = false;
        hasMore = true;
        loadedContacts.clear();
        if (contactsFlow != null) {
            contactsFlow.getChildren().clear();
        }
        PerfLog.log("contacts.page", "load.start", "generation=" + loadGeneration + " queryLength=" + normalizedQuery().length());
        loadNextPage();
        PerfLog.logDone("contacts.page", "phase=reset generation=" + loadGeneration, started);
    }

    private void loadNextPage() {
        if (loading || !hasMore) {
            return;
        }

        final int generationAtSubmit = loadGeneration;

        if (contactService == null) {
            loadedContacts.clear();
            setEmptyStateMessage("Contacts are unavailable right now.");
            showEmptyState();
            return;
        }

        Integer tenantId = appState == null ? null : appState.getShaleClientId();
        Integer actorId = appState == null ? null : appState.getUserId();
        if (tenantId == null || tenantId <= 0 || actorId == null || actorId <= 0) {
            loadedContacts.clear();
            setEmptyStateMessage("No tenant is selected.");
            showEmptyState();
            return;
        }

        loading = true;
        final int pageToLoad = currentPage;
        final String queryAtSubmit = normalizedQuery();
        final ContactServicePort.DirectoryFilters filtersAtSubmit=filters();
        latestRequestedQuery = queryAtSubmit;
        final long queryStarted = PerfLog.start();
        if (pageToLoad == 0) { pageLoadStartedNanos = queryStarted; }
        PerfLog.log("contacts.search", "queued", "generation=" + generationAtSubmit + " page=" + pageToLoad + " pageSize=" + pageSize + " tenantId=" + tenantId + " queryLength=" + queryAtSubmit.length());
        if (loadedContacts.isEmpty()) {
            setLoadingStateMessage("Loading contacts…");
            updateLoadingState(true);
        } else {
            rerender();
        }

        dbExec.submit(() -> {
            try {
                if (generationAtSubmit != loadGeneration || !queryAtSubmit.equals(latestRequestedQuery)) {
                    PerfLog.logDone("contacts.search", "phase=skipBeforeDao generation=" + generationAtSubmit + " page=" + pageToLoad + " reason=staleQueued latestGeneration=" + loadGeneration, queryStarted);
                    return;
                }
                long daoStarted = PerfLog.start();
                PerfLog.log("contacts.search.dao", "start", "generation=" + generationAtSubmit + " page=" + pageToLoad + " tenantId=" + tenantId + " queryLength=" + queryAtSubmit.length());
                var page = contactService.getContactDirectoryPage(tenantId, actorId, pageToLoad, pageSize, queryAtSubmit, filtersAtSubmit);
                PerfLog.logDone("contacts.search.dao", "generation=" + generationAtSubmit + " page=" + pageToLoad + " tenantId=" + tenantId + " rows=" + page.items().size() + " total=" + page.total(), daoStarted);

                Platform.runLater(() -> {
                    if (generationAtSubmit != loadGeneration || !queryAtSubmit.equals(normalizedQuery())) {
                        PerfLog.logDone("contacts.search", "phase=discardAfterDao generation=" + generationAtSubmit + " page=" + pageToLoad + " reason=stale latestGeneration=" + loadGeneration, queryStarted);
                        return;
                    }
                    loadedContacts.addAll(page.items());
                    currentPage++;
                    hasMore = loadedContacts.size() < page.total();
                    loading = false;
                    setEmptyStateMessage("No contacts to display yet.");
                    rerender();
                    PerfLog.logDone("contacts.search", "phase=apply generation=" + generationAtSubmit + " page=" + pageToLoad + " loaded=" + loadedContacts.size() + " hasMore=" + hasMore, queryStarted);
                    if (pageToLoad == 0) { PerfLog.logDone("contacts.page", "phase=initialLoad generation=" + generationAtSubmit + " rows=" + loadedContacts.size(), pageLoadStartedNanos); }
                });
            } catch (RuntimeException ex) {
                LOG.error("Unable to load Contact directory page {} for tenant {}", pageToLoad, tenantId, ex);
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

    private void handleContactUpdated(UiRuntimeBridge.EntityUpdatedEvent event) {
        if (event == null || !LiveUpdateEvents.ENTITY_CONTACT.equals(event.entityType()) || appState == null) return;
        Integer tenantId = appState.getShaleClientId();
        if (tenantId == null || event.shaleClientId() != tenantId) return;
        Platform.runLater(this::loadFirstPage);
    }

    private void rerender() {
        long renderStarted = PerfLog.start();
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
        if (empty && filters().activeCount()>0) {
            setEmptyStateMessage("No contacts match the selected filters.");
        } else if (empty && !query.isBlank()) {
            setEmptyStateMessage("No contacts match your search.");
        } else if (empty) {
            setEmptyStateMessage(emptyStateMessage);
        }
        updateLoadingState(false);
        updateEmptyState(empty);
        PerfLog.logDone("contacts.render", "cards=" + cards.size() + " loaded=" + loadedContacts.size() + " loading=" + loading + " fxThread=" + Platform.isFxApplicationThread(), renderStarted);
    }

    private Node buildCard(ContactCardSummary row) {
        var card = contactCardFactory.create(cardModel(row), CONTACTS_CARD_VARIANT);
        card.setMinHeight(CONTACT_CARD_HEIGHT);
        card.setPrefHeight(CONTACT_CARD_HEIGHT);
        card.setPrefWidth(CONTACT_CARD_WIDTH);
        card.setMaxWidth(CONTACT_CARD_WIDTH);
        return card;
    }

    static ContactCardModel cardModel(ContactCardSummary row) {
        String displayName = safe(row.displayName()).isBlank() ? "—" : safe(row.displayName());
        return new ContactCardModel(row.id(), displayName, null, row.email(), row.phone());
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
        if (contactsListRegion != null) {
            contactsListRegion.showEmpty(empty);
            return;
        }

        if (empty) {
            UiStateLabels.showEmpty(contactsEmptyStateLabel);
        } else {
            UiStateLabels.hide(contactsEmptyStateLabel);
        }
        if (contactsScroll != null) {
            contactsScroll.setVisible(!empty);
            contactsScroll.setManaged(!empty);
        }
    }

    private void updateLoadingState(boolean loadingStateVisible) {
        if (contactsListRegion != null) {
            contactsListRegion.showLoading(loadingStateVisible);
            return;
        }

        if (loadingStateVisible) {
            UiStateLabels.showLoading(contactsLoadingStateLabel);
            UiStateLabels.hide(contactsEmptyStateLabel);
        } else {
            UiStateLabels.hide(contactsLoadingStateLabel);
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
