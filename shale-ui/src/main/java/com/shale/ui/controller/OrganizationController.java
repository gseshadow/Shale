package com.shale.ui.controller;

import com.shale.ui.component.richtext.NarrativeMarkdownCodec;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import com.shale.core.model.Organization;
import com.shale.core.service.OrganizationServicePort;
import com.shale.core.service.OrganizationServicePort.OrganizationAggregateResult;
import com.shale.data.dao.OrganizationDao;
import com.shale.data.dao.CaseSummaryDao;
import com.shale.data.dao.CaseSummaryDao.RelatedCaseRow;
import com.shale.ui.component.dialog.AppDialogs;
import com.shale.ui.component.ClassificationChipGroup;
import com.shale.ui.component.ContactMethodDisplayCard;
import com.shale.ui.component.factory.CaseCardFactory;
import com.shale.ui.component.factory.CaseCardFactory.CaseCardModel;
import com.shale.ui.controller.support.CaseListFilterSortSupport;
import com.shale.ui.services.UiRuntimeBridge;
import com.shale.ui.state.AppState;
import com.shale.ui.util.ControlStyles;
import com.shale.ui.util.PerfLog;
import com.shale.ui.util.ContactExternalActions;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

public final class OrganizationController {

	private static final Map<String, Organization> DETAIL_CACHE = new ConcurrentHashMap<>();

	@FXML private Label organizationTitleLabel;
	@FXML private Label lastUpdatedLabel;
	@FXML private Label errorLabel;
	@FXML private Button editButton;
	@FXML private Button deleteOrganizationButton;
	@FXML private HBox remoteUpdateBanner;
	@FXML private Button reloadRemoteButton;
	@FXML private VBox relatedCasesContainer;
	@FXML private Label relatedCasesEmptyLabel;
	@FXML private TextField relatedCasesSearchField;
	@FXML private ChoiceBox<String> relatedCasesSortChoice;

	@FXML private FlowPane organizationTypeChips;
	@FXML private TilePane phoneCards;
	@FXML private TilePane emailCards;
	@FXML private VBox addressCards;
	@FXML private VBox websiteCards;
	@FXML private VBox phoneSection;
	@FXML private VBox emailSection;
	@FXML private VBox addressSection;
	@FXML private VBox websiteSection;
	@FXML private Label notesValue;

	private Integer organizationId;
	private OrganizationDao organizationDao;
	private OrganizationServicePort organizationService;
	private CaseSummaryDao caseSummaryDao;
	private Organization currentOrganization;
	private OrganizationServicePort.OrganizationTypeProfile currentTypeProfile;
	private OrganizationServicePort.OrganizationStructuredContactProfile currentContactProfile;
	private int detailLoadGeneration;
	private ContactExternalActions externalActions = new ContactExternalActions();
	private boolean editDialogOpen;
	private AppState appState;
	private UiRuntimeBridge runtimeBridge;
	private Consumer<UiRuntimeBridge.EntityUpdatedEvent> liveOrganizationUpdatedHandler;
	private boolean liveSubscribed;
	private boolean pendingRemoteUpdate;
	private boolean awaitingAuthoritativeReloadAfterLocalSave;
	private int relatedCasesLoadGeneration;
	private List<RelatedCaseRow> relatedCases = List.of();
	private CaseCardFactory caseCardFactory;
	private Consumer<Integer> onOpenCase;
	private Runnable onOrganizationDeleted;

	private final ExecutorService dbExec = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "organization-detail-loader");
		t.setDaemon(true);
		return t;
	});

	public void init(
			int organizationId,
			OrganizationDao organizationDao,
			OrganizationServicePort organizationService,
			CaseSummaryDao caseSummaryDao,
			AppState appState,
			UiRuntimeBridge runtimeBridge,
			Consumer<Integer> onOpenCase,
			Runnable onOrganizationDeleted) {
		this.organizationId = organizationId;
		this.organizationDao = organizationDao;
		this.organizationService = Objects.requireNonNull(organizationService,"organizationService");
		this.caseSummaryDao = Objects.requireNonNull(caseSummaryDao, "caseSummaryDao");
		this.appState = appState;
		this.runtimeBridge = runtimeBridge;
		this.onOpenCase = onOpenCase;
		this.onOrganizationDeleted = onOrganizationDeleted;
		this.caseCardFactory = new CaseCardFactory(onOpenCase);
	}

	@FXML
	private void initialize() {
		if (editButton != null) {
			ControlStyles.apply(editButton, ControlStyles.Purpose.SECONDARY);
			editButton.setOnAction(e -> onEdit());
			setVisibleManaged(editButton, false);
		}
		if (deleteOrganizationButton != null) {
			ControlStyles.apply(deleteOrganizationButton, ControlStyles.Purpose.DANGER);
			deleteOrganizationButton.setOnAction(e -> onDeleteOrganization());
			setVisibleManaged(deleteOrganizationButton, false);
		}
		if (reloadRemoteButton != null) {
			reloadRemoteButton.setOnAction(e -> onReloadRemote());
		}
		CaseListFilterSortSupport.initializeControls(relatedCasesSearchField, relatedCasesSortChoice, this::renderRelatedCases);

		hideRemoteUpdateBanner();
		refreshAdminActions();
		if(phoneCards!=null)phoneCards.widthProperty().addListener((o,a,b)->configureMethodTiles(phoneCards));
		if(emailCards!=null)emailCards.widthProperty().addListener((o,a,b)->configureMethodTiles(emailCards));

		if (organizationTitleLabel != null) {
			organizationTitleLabel.sceneProperty().addListener((obs, oldScene, newScene) -> {
				if (newScene == null) {
					unsubscribeLiveOrganizationUpdates();
				} else {
					subscribeLiveOrganizationUpdates();
				}
			});
		}

		subscribeLiveOrganizationUpdates();
		Platform.runLater(this::loadOrganization);
	}

	private void loadOrganization() {
		final int generation=++detailLoadGeneration;
		final Integer requestedId=organizationId,requestedTenant=currentTenantId();
		long loadStarted = PerfLog.start();
		if (organizationDao == null || organizationId == null) {
			setError("Organization view is not configured.");
			return;
		}

		setBusy(true);
		PerfLog.log("organizations.detail.load", "queued", "organizationId=" + organizationId + " tenantId=" + currentTenantId());
		dbExec.submit(() -> {
			try {
				String cacheKey = detailCacheKey(requestedId);
				Organization loaded;
				boolean cacheHit = false;
				{
					long daoStarted = PerfLog.start();
					PerfLog.log("organizations.detail.dao", "start", "organizationId=" + requestedId + " tenantId=" + requestedTenant + " cacheHit=false");
					loaded = organizationDao.findById(requestedId);
					PerfLog.logDone("organizations.detail.dao", "organizationId=" + requestedId + " found=" + (loaded != null) + " fullDetailHydration=true", daoStarted);
					if (loaded != null && cacheKey != null) {
						DETAIL_CACHE.put(cacheKey, loaded);
					}
				}
				OrganizationServicePort.OrganizationTypeProfile types=requestedTenant==null?null:organizationService.getOrganizationTypeProfile(requestedId,requestedTenant).orElse(null);
				OrganizationServicePort.OrganizationStructuredContactProfile contacts=requestedTenant==null?null:organizationService.findStructuredContactProfile(requestedTenant,requestedId).orElse(null);
				final Organization loadedForUi = loaded;
				final boolean cacheHitForUi = cacheHit;
				Platform.runLater(() -> {
					if(generation!=detailLoadGeneration||!Objects.equals(requestedId,organizationId)||!Objects.equals(requestedTenant,currentTenantId()))return;
					setBusy(false);
					if (loadedForUi == null) {
						relatedCases = List.of();
						renderRelatedCases();
						setError("Organization not found.");
						return;
					}

					currentOrganization = loadedForUi;
					currentTypeProfile=types; currentContactProfile=contacts;
					awaitingAuthoritativeReloadAfterLocalSave=false;
					resetRelatedCaseControls();
					renderFromCurrent();
					clearError();
					if((currentTypeProfile!=null&&!currentTypeProfile.compatibilityConsistent())||(currentContactProfile!=null&&!currentContactProfile.compatibilityConsistent()))setError("Some organization information needs review before it is edited.");
					PerfLog.logDone("organizations.detail.load", "phase=detailApplied organizationId=" + organizationId + " cacheHit=" + cacheHitForUi, loadStarted);
					loadRelatedCasesSafe();
				});
			} catch (Exception ex) {
			Platform.runLater(() -> {
					if(generation!=detailLoadGeneration)return;
					awaitingAuthoritativeReloadAfterLocalSave=false;
					setBusy(false);
					setError("Failed to load organization details.");
				});
			}
		});
	}

	private void loadRelatedCasesSafe() {
		long relatedStarted = PerfLog.start();
		final int generation = ++relatedCasesLoadGeneration;
		final Integer requestedOrganizationId = organizationId;
		final Integer requestedTenantId = currentTenantId();
		if (organizationDao == null || organizationId == null) {
			relatedCases = List.of();
			renderRelatedCases();
			return;
		}

		dbExec.submit(() -> {
			try {
				PerfLog.log("organizations.relatedCases.dao", "start", "organizationId=" + organizationId);
				List<RelatedCaseRow> loadedRelatedCases = caseSummaryDao.listActiveRelatedToOrganization(
						requestedTenantId == null ? 0 : requestedTenantId, requestedOrganizationId);
				int rowCount = loadedRelatedCases == null ? 0 : loadedRelatedCases.size();
				Platform.runLater(() -> {
					if (generation != relatedCasesLoadGeneration || !Objects.equals(organizationId, requestedOrganizationId)
							|| !Objects.equals(currentTenantId(), requestedTenantId)) return;
					relatedCases = loadedRelatedCases == null ? List.of() : loadedRelatedCases;
					renderRelatedCases();
					PerfLog.logDone("organizations.relatedCases.load", "organizationId=" + organizationId + " rows=" + rowCount, relatedStarted);
				});
			} catch (Exception ex) {
				Platform.runLater(() -> {
					if (generation != relatedCasesLoadGeneration || !Objects.equals(organizationId, requestedOrganizationId)
							|| !Objects.equals(currentTenantId(), requestedTenantId)) return;
					relatedCases = List.of();
					renderRelatedCases();
				});
			}
		});
	}


	private void onEdit() {
		if (currentOrganization == null || !canEditOrganization() || editDialogOpen) return;
		editDialogOpen = true;
		new EditOrganizationDialog(currentOrganization.getId(), organizationDao, organizationService, appState, dbExec,
				this::applySuccessfulAggregateSave, () -> { editDialogOpen = false; refreshAdminActions(); })
				.show(dialogOwner(editButton));
		refreshAdminActions();
	}

	private void applySuccessfulAggregateSave(OrganizationAggregateResult result) {
		int updatedId = result.organizationId();
		pendingRemoteUpdate = false;
		hideRemoteUpdateBanner();
		invalidateDetailCache(updatedId);
		awaitingAuthoritativeReloadAfterLocalSave = true;
		loadOrganization();
		publishOrganizationUpdated(updatedId);
	}

	private void onDeleteOrganization() {
		long deleteStarted = PerfLog.start();
		if (organizationDao == null || currentOrganization == null || currentOrganization.getId() == null) {
			setError("Organization details are unavailable.");
			return;
		}
		if (!isAdminUser()) {
			setError("Only admin users can delete organizations.");
			return;
		}
		if (!confirmDeleteOrganization()) {
			return;
		}

		setBusy(true);
		dbExec.submit(() -> {
			try {
				PerfLog.log("organizations.delete", "start", "organizationId=" + currentOrganization.getId() + " tenantId=" + appState.getShaleClientId());
				boolean deleted = organizationDao.softDeleteOrganization(currentOrganization.getId(), appState.getShaleClientId());
				if (deleted) {
					invalidateDetailCache(currentOrganization.getId());
				}
				Platform.runLater(() -> {
					setBusy(false);
					if (!deleted) {
						setError("Organization could not be deleted.");
						return;
					}
					publishOrganizationUpdated(currentOrganization.getId());
					pendingRemoteUpdate = false;
					hideRemoteUpdateBanner();
					clearError();
					navigateAfterDelete();
					PerfLog.logDone("organizations.delete", "phase=apply organizationId=" + currentOrganization.getId(), deleteStarted);
				});
			} catch (Exception ex) {
				Platform.runLater(() -> {
					setBusy(false);
					setError("Failed to delete organization.");
				});
			}
		});
	}

	private boolean confirmDeleteOrganization() {
		Window owner = dialogOwner(deleteOrganizationButton);
		if (owner == null) {
			owner = dialogOwner(editButton);
		}
		return AppDialogs.showConfirmation(
				owner,
				"Delete Organization",
				"Delete this organization?",
				"This will remove it from active lists.",
				"Delete Organization",
				AppDialogs.DialogActionKind.DANGER);
	}

	private Window dialogOwner(Button button) {
		if (button != null && button.getScene() != null) {
			return button.getScene().getWindow();
		}
		return null;
	}

	private void navigateAfterDelete() {
		if (onOrganizationDeleted != null) {
			onOrganizationDeleted.run();
		}
	}

	private void onReloadRemote() {
		pendingRemoteUpdate = false;
		hideRemoteUpdateBanner();
		loadOrganization();
	}

	private void subscribeLiveOrganizationUpdates() {
		if (runtimeBridge == null || liveSubscribed) {
			return;
		}

		liveOrganizationUpdatedHandler = this::handleLiveOrganizationUpdatedEvent;
		runtimeBridge.subscribeEntityUpdated(liveOrganizationUpdatedHandler);
		liveSubscribed = true;
	}

	private void unsubscribeLiveOrganizationUpdates() {
		if (!liveSubscribed || runtimeBridge == null || liveOrganizationUpdatedHandler == null) {
			return;
		}

		runtimeBridge.unsubscribeEntityUpdated(liveOrganizationUpdatedHandler);
		liveSubscribed = false;
	}

	private void handleLiveOrganizationUpdatedEvent(UiRuntimeBridge.EntityUpdatedEvent event) {
		if (shouldIgnoreLiveEvent(event)) {
			return;
		}

		runOnFx(() -> {
			if (editDialogOpen) {
				pendingRemoteUpdate = true;
				showRemoteUpdateBanner();
				return;
			}

			onReloadRemote();
		});
	}

	private boolean shouldIgnoreLiveEvent(UiRuntimeBridge.EntityUpdatedEvent event) {
		if (event == null || organizationId == null || event.entityType() == null) {
			return true;
		}
		if (!"Organization".equals(event.entityType())) {
			return true;
		}
		if (event.entityId() != organizationId.longValue()) {
			return true;
		}
		if (awaitingAuthoritativeReloadAfterLocalSave && appState != null
				&& Objects.equals(appState.getUserId(), event.updatedByUserId())) {
			return true;
		}
		return isOwnEcho(event);
	}

	private boolean isOwnEcho(UiRuntimeBridge.EntityUpdatedEvent event) {
		if (runtimeBridge == null) {
			return false;
		}
		String mine = runtimeBridge.getClientInstanceId();
		return mine != null && !mine.isBlank() && mine.equals(event.clientInstanceId());
	}

	private void showRemoteUpdateBanner() {
		setVisibleManaged(remoteUpdateBanner, true);
		setVisibleManaged(reloadRemoteButton, true);
	}

	private void hideRemoteUpdateBanner() {
		setVisibleManaged(remoteUpdateBanner, false);
		setVisibleManaged(reloadRemoteButton, false);
	}

	private void publishOrganizationUpdated(Integer organizationId) {
		if (organizationId == null || organizationId <= 0 || appState == null || runtimeBridge == null
				|| appState.getShaleClientId() == null || appState.getUserId() == null) {
			return;
		}

		try {
			int clientId = appState.getShaleClientId();
			int userId = appState.getUserId();
			runtimeBridge.publishOrganizationUpdated(organizationId, clientId, userId);
		} catch (Exception ex) {
			System.out.println("OrganizationUpdated publish skipped: " + ex.getMessage());
		}
	}

	private void renderFromCurrent() {
		long renderStarted = PerfLog.start();
		Organization o = currentOrganization;
		if (o == null) {
			return;
		}

		organizationTitleLabel.setText(fallback(o.getName()));
		renderTypeChips();
		renderContactInformation();
		notesValue.setText(NarrativeMarkdownCodec.plainText(fallback(o.getNotes())));

		if (o.getUpdatedAt() != null) {
			String formatted = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a")
					.withZone(ZoneId.systemDefault())
					.format(o.getUpdatedAt());
			lastUpdatedLabel.setText("Last updated: " + formatted);
		} else {
			lastUpdatedLabel.setText("Last updated: —");
		}

		refreshAdminActions();
		PerfLog.logDone("organizations.detail.render", "organizationId=" + (o == null ? null : o.getId()) + " fxThread=" + Platform.isFxApplicationThread(), renderStarted);
	}

	private void renderTypeChips(){
		if(organizationTypeChips==null)return;
		List<ClassificationChipGroup.Chip> chips=currentTypeProfile==null?List.of():currentTypeProfile.assignments().stream()
				.filter(a->!a.historical()).sorted(Comparator.comparing(OrganizationServicePort.AssignedOrganizationType::primary).reversed().thenComparingInt(OrganizationServicePort.AssignedOrganizationType::sortOrder).thenComparingLong(OrganizationServicePort.AssignedOrganizationType::assignmentId))
				.map(a->new ClassificationChipGroup.Chip(a.definition().name(),a.definition().color(),"Organization Type",a.definition().organizationTypeId(),a.primary())).toList();
		organizationTypeChips.getChildren().setAll(new ClassificationChipGroup(chips,ClassificationChipGroup.Size.STANDARD));
	}

	private void renderContactInformation(){
		if(phoneCards==null||emailCards==null||addressCards==null||websiteCards==null)return;
		phoneCards.getChildren().clear();emailCards.getChildren().clear();addressCards.getChildren().clear();websiteCards.getChildren().clear();
		if(currentContactProfile!=null){
			currentContactProfile.activePhones().stream().sorted(contactOrder(OrganizationServicePort.OrganizationPhoneNumber::primary,OrganizationServicePort.OrganizationPhoneNumber::sortOrder,OrganizationServicePort.OrganizationPhoneNumber::id)).forEach(p->phoneCards.getChildren().add(methodCard(phoneDisplay(p),kindLabel(p.kind(),p.rawKind()),p.primary(),p.fax()?null:"Call",()->externalActions.open(ContactExternalActions.telephone(p.normalizedNumber(),p.extension())))));
			currentContactProfile.activeEmails().stream().sorted(contactOrder(OrganizationServicePort.OrganizationEmailAddress::primary,OrganizationServicePort.OrganizationEmailAddress::sortOrder,OrganizationServicePort.OrganizationEmailAddress::id)).forEach(e->emailCards.getChildren().add(methodCard(e.emailAddress(),kindLabel(e.kind(),e.rawKind()),e.primary(),validEmail(e.emailAddress())?"Email":null,()->externalActions.open(ContactExternalActions.email(e.emailAddress())))));
			currentContactProfile.activeAddresses().stream().sorted(contactOrder(OrganizationServicePort.OrganizationAddress::primary,OrganizationServicePort.OrganizationAddress::sortOrder,OrganizationServicePort.OrganizationAddress::id)).forEach(a->{String value=formatAddress(a);addressCards.getChildren().add(methodCard(value,kindLabel(a.kind(),a.rawKind()),a.primary(),value.isBlank()?null:"Open address in maps",()->externalActions.open(ContactExternalActions.maps(value))));});
			currentContactProfile.activeWebsites().stream().sorted(contactOrder(OrganizationServicePort.OrganizationWebsite::primary,OrganizationServicePort.OrganizationWebsite::sortOrder,OrganizationServicePort.OrganizationWebsite::id)).forEach(w->websiteCards.getChildren().add(methodCard(w.website(),kindLabel(w.kind(),w.rawKind()),w.primary(),safeWebsite(w.website())?"Open Website":null,()->externalActions.open(ContactExternalActions.website(w.website())))));
		}
		showGroup(phoneSection,!phoneCards.getChildren().isEmpty());showGroup(emailSection,!emailCards.getChildren().isEmpty());showGroup(addressSection,!addressCards.getChildren().isEmpty());showGroup(websiteSection,!websiteCards.getChildren().isEmpty());
	}
	private Node methodCard(String value,String kind,boolean primary,String action,Runnable launch){
		return new ContactMethodDisplayCard(value,kind,primary,action,()->{try{launch.run();}catch(RuntimeException ex){AppDialogs.showError(dialogOwner(editButton),"Open External Action","Unable to open this item.");}});
	}
	private static void showGroup(Node node,boolean show){if(node!=null){node.setVisible(show);node.setManaged(show);}}
	private static String phoneDisplay(OrganizationServicePort.OrganizationPhoneNumber p){return p.displayNumber()+(p.extension()==null||p.extension().isBlank()?"":" ext. "+p.extension());}
	private static String kindLabel(Enum<?> kind,String raw){if(kind==null||"UNKNOWN".equals(kind.name()))return readable(raw);return readable(kind.name());}
	private static String readable(String value){if(value==null||value.isBlank())return "Other";String s=value.trim().replace('_',' ').toLowerCase();return Character.toUpperCase(s.charAt(0))+s.substring(1);}
	private static String formatAddress(OrganizationServicePort.OrganizationAddress a){return java.util.stream.Stream.of(a.addressLine1(),a.addressLine2(),a.city(),a.stateOrProvince(),a.postalCode(),a.country()).filter(v->v!=null&&!v.isBlank()).map(String::trim).collect(java.util.stream.Collectors.joining(", "));}
	private static boolean validEmail(String value){return value!=null&&value.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");}
	private static boolean safeWebsite(String value){try{ContactExternalActions.website(value);return true;}catch(IllegalArgumentException ex){return false;}}
	private static <T> Comparator<T> contactOrder(java.util.function.Predicate<T> primary,java.util.function.ToIntFunction<T> order,java.util.function.ToLongFunction<T> id){return Comparator.<T,Boolean>comparing(primary::test).reversed().thenComparingInt(order).thenComparingLong(id);}
	private static void configureMethodTiles(TilePane pane){double width=pane.getWidth();boolean two=width>=600;pane.setPrefColumns(two?2:1);pane.setPrefTileWidth(two?Math.max(250,(width-pane.getHgap())/2):Math.max(250,width));}

	private void renderRelatedCases() {
		if (!Platform.isFxApplicationThread()) {
			runOnFx(this::renderRelatedCases);
			return;
		}

		if (relatedCasesContainer == null) {
			return;
		}

		if (caseCardFactory == null) {
			caseCardFactory = new CaseCardFactory(onOpenCase);
		}
		String query = CaseListFilterSortSupport.normalizedQuery(relatedCasesSearchField);
		Comparator<RelatedCaseRow> comparator = CaseListFilterSortSupport.comparator(
				relatedCasesSortChoice,
				row -> row.summary().caseName(),
				RelatedCaseRow::intakeDate,
				RelatedCaseRow::statuteOfLimitationsDate);

		List<Node> cards = relatedCases.stream()
				.filter(row -> CaseListFilterSortSupport.matchesQuery(query, row.summary().caseName(), row.summary().responsibleAttorneyName()))
				.sorted(comparator)
				.map(this::createRelatedCaseCardContainer)
				.toList();

		relatedCasesContainer.getChildren().setAll(cards);

		boolean empty = cards.isEmpty();
		if (relatedCasesEmptyLabel != null) {
			relatedCasesEmptyLabel.setVisible(empty);
			relatedCasesEmptyLabel.setManaged(empty);
			if (empty) {
				relatedCasesEmptyLabel.toFront();
				if (!query.isEmpty()) {
					relatedCasesEmptyLabel.setText("No related cases match your search");
				} else {
					relatedCasesEmptyLabel.setText("No related cases");
				}
			} else {
				relatedCasesContainer.toFront();
			}
		}
	}

	private Node createRelatedCaseCardContainer(RelatedCaseRow row) {
		Node card = caseCardFactory.create(toRelatedCaseCardModel(row), CaseCardFactory.Variant.FULL);
		if (card instanceof Region region) {
			region.setMaxWidth(Double.MAX_VALUE);
			region.setPrefWidth(380);
			region.setMaxWidth(420);
		}
		Label relationshipMeta = new Label(formatRelationshipMeta(row.partyRoleName(), row.side(), row.primary()));
		relationshipMeta.getStyleClass().add("muted");
		relationshipMeta.setWrapText(true);
		return new VBox(4, card, relationshipMeta);
	}

	static CaseCardModel toRelatedCaseCardModel(RelatedCaseRow row) {
		return new CaseCardModel(
				row.summary().caseId(),
				row.summary().caseName(),
				row.intakeDate(),
				row.statuteOfLimitationsDate(),
				row.tortClaimsNoticeDeadline(),
				row.summary().responsibleAttorneyName(),
				row.summary().responsibleAttorneyColor(),
				row.nonEngagementLetterSent(),
				row.summary().primaryStatusName(),
				row.summary().primaryStatusColor(),
				row.practiceAreaColor()
		);
	}

	private static String formatRelationshipMeta(String roleName, String side, boolean primary) {
		String role = safe(roleName).isBlank() ? "Relationship" : safe(roleName).trim();
		String sideLabel = safe(side).isBlank() ? "unclassified" : safe(side).trim();
		return primary ? role + " • " + sideLabel + " • primary" : role + " • " + sideLabel;
	}

	private Integer currentTenantId() {
		return appState == null ? null : appState.getShaleClientId();
	}

	private String detailCacheKey(Integer id) {
		Integer tenantId = currentTenantId();
		if (tenantId == null || tenantId <= 0 || id == null || id <= 0) {
			return null;
		}
		return tenantId + ":" + id;
	}

	private void cacheDetail(Organization organization) {
		if (organization == null) {
			return;
		}
		String cacheKey = detailCacheKey(organization.getId());
		if (cacheKey != null) {
			DETAIL_CACHE.put(cacheKey, organization);
		}
	}

	private void invalidateDetailCache(Integer id) {
		String cacheKey = detailCacheKey(id);
		if (cacheKey != null) {
			DETAIL_CACHE.remove(cacheKey);
			PerfLog.log("organizations.detail.cache", "invalidate", "organizationId=" + id + " tenantId=" + currentTenantId());
		}
	}

	private static String safe(String value) {
		return value == null ? "" : value;
	}

	private void resetRelatedCaseControls() {
		CaseListFilterSortSupport.resetControls(relatedCasesSearchField, relatedCasesSortChoice);
	}

	private void setBusy(boolean busy) {
		if (editButton != null) editButton.setDisable(busy);
		if (deleteOrganizationButton != null) deleteOrganizationButton.setDisable(busy);
	}

	private void refreshAdminActions() {
		setVisibleManaged(editButton, canEditOrganization() && !editDialogOpen && currentOrganization != null);
		boolean showDelete = isAdminUser() && currentOrganization != null;
		setVisibleManaged(deleteOrganizationButton, showDelete);
	}

	private boolean canEditOrganization() {
		Integer userId = appState == null ? null : appState.getUserId();
		return userId != null && userId > 0;
	}

	private boolean isAdminUser() {
		return appState != null && appState.isAdmin();
	}

	private static void runOnFx(Runnable runnable) {
		if (Platform.isFxApplicationThread()) {
			runnable.run();
		} else {
			Platform.runLater(runnable);
		}
	}

	private static void setVisibleManaged(javafx.scene.Node node, boolean visible) {
		if (node == null) {
			return;
		}
		node.setVisible(visible);
		node.setManaged(visible);
	}

	private void setError(String message) {
		if (errorLabel == null) {
			return;
		}
		errorLabel.setText(message);
		errorLabel.setVisible(true);
		errorLabel.setManaged(true);
	}

	private void clearError() {
		if (errorLabel == null) {
			return;
		}
		errorLabel.setText("");
		errorLabel.setVisible(false);
		errorLabel.setManaged(false);
	}

	private static String fallback(String text) {
		if (text == null || text.isBlank()) {
			return "—";
		}
		return text;
	}

	private static String safeText(String text) {
		if (text == null) {
			return "";
		}
		return text.trim();
	}

}
