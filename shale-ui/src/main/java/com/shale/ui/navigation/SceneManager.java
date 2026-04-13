package com.shale.ui.navigation;

import com.shale.core.runtime.DbSessionProvider;
import com.shale.core.dto.TaskDetailDto;
import com.shale.core.dto.TaskPriorityOptionDto;
import com.shale.core.dto.TaskStatusOptionDto;
import com.shale.data.dao.CaseDao;
import com.shale.data.dao.ContactDao;
import com.shale.data.dao.OrganizationDao;
import com.shale.data.dao.UserDao;
import com.shale.data.dao.TaskDao;
import com.shale.data.dao.NotificationDao;
import com.shale.data.dao.UserPreferencesDao;
import com.shale.ui.controller.CaseController;
import com.shale.ui.controller.CasesController;
import com.shale.ui.controller.ContactViewController;
import com.shale.ui.controller.ContactsController;
import com.shale.ui.controller.LoginController;
import com.shale.ui.controller.MainController;
import com.shale.ui.controller.MyShaleController;
import com.shale.ui.controller.NewIntakeController;
import com.shale.ui.controller.NewOrganizationController;
import com.shale.ui.controller.OrganizationController;
import com.shale.ui.controller.OrganizationsController;
import com.shale.ui.controller.SearchController;
import com.shale.ui.controller.SettingsController;
import com.shale.ui.controller.TeamController;
import com.shale.ui.controller.UserController;
import com.shale.ui.component.dialog.AppDialogs;
import com.shale.ui.component.dialog.TaskDetailDialog;
import com.shale.ui.services.CaseDetailService;
import com.shale.ui.services.ContactDetailService;
import com.shale.ui.services.CaseTaskService;
import com.shale.ui.services.SearchService;
import com.shale.ui.services.UserDetailService;
import com.shale.ui.services.UiAuthService;
import com.shale.ui.services.UiRuntimeBridge;
import com.shale.ui.services.UserPreferencesService;
import com.shale.ui.services.UpdatePollingService;
import com.shale.ui.state.AppState;
import com.shale.ui.util.PerfLog;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.fxml.LoadException;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.time.Clock;
import java.net.URL;
import java.util.Objects;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import javafx.stage.Window;

import com.shale.ui.services.UiUpdateLauncher;
import com.shale.ui.services.UiUpdateLauncher.UpdateCheckResult;
import com.shale.ui.notification.NotificationCenterService;
import com.shale.ui.notification.LiveUpdateNotificationBridge;
import com.shale.ui.notification.ConnectivityNotificationProducer;
import com.shale.ui.notification.SystemUpdateNotificationProducer;
import com.shale.ui.notification.NotificationPreferencesService;
import com.shale.ui.notification.DurableNotificationService;
import com.shale.ui.notification.AssignedUserTaskDueNotificationRecipientResolver;
import com.shale.ui.notification.TaskDueDateNotificationGenerator;

public final class SceneManager {

	private final Stage stage;
	private final AppState appState;
	private final UiAuthService authService;
	private final UiRuntimeBridge runtimeBridge;
	private final DbSessionProvider dbSessionProvider;
	private final UiUpdateLauncher updateLauncher;
	private final NavigationManager navigationManager = new NavigationManager();
	private final NotificationCenterService notificationCenterService;
	private final LiveUpdateNotificationBridge liveUpdateNotificationBridge;
	private final ConnectivityNotificationProducer connectivityNotificationProducer;
	private final SystemUpdateNotificationProducer systemUpdateNotificationProducer;
	private final NotificationPreferencesService notificationPreferencesService;
	private final DurableNotificationService durableNotificationService;
	private final TaskDueDateNotificationGenerator taskDueDateNotificationGenerator;
	private final UpdatePollingService updatePollingService;
	private final ExecutorService notificationStartupExecutor;
	private final AtomicLong notificationStartupGeneration = new AtomicLong(0);
	private volatile Future<?> notificationStartupFuture;

	public SceneManager(Stage stage,
			AppState appState,
			UiAuthService authService,
			UiRuntimeBridge runtimeBridge,
			DbSessionProvider dbSessionProvider,
			UiUpdateLauncher updateLauncher) {
		this.stage = stage;
		this.appState = appState;
		this.authService = authService;
		this.runtimeBridge = runtimeBridge;
		this.dbSessionProvider = Objects.requireNonNull(dbSessionProvider);
		this.updateLauncher = Objects.requireNonNull(updateLauncher);
		this.notificationCenterService = createNotificationCenterService();
		UserPreferencesService userPreferencesService = new UserPreferencesService(new UserPreferencesDao(dbSessionProvider), appState);
		this.notificationPreferencesService = new NotificationPreferencesService(appState, userPreferencesService);
		this.durableNotificationService = new DurableNotificationService(new NotificationDao(dbSessionProvider), appState, notificationPreferencesService);
		this.taskDueDateNotificationGenerator = new TaskDueDateNotificationGenerator(
				new TaskDao(dbSessionProvider),
				new NotificationDao(dbSessionProvider),
				appState,
				notificationPreferencesService,
				new AssignedUserTaskDueNotificationRecipientResolver());
		this.notificationStartupExecutor = Executors.newSingleThreadExecutor(r -> {
			Thread t = new Thread(r, "notification-startup-worker");
			t.setDaemon(true);
			return t;
		});
		this.notificationCenterService.setReadListener(durableNotificationService::markRead);
		this.notificationCenterService.setDismissListener(durableNotificationService::dismiss);
		this.liveUpdateNotificationBridge = new LiveUpdateNotificationBridge(runtimeBridge, appState, notificationCenterService, notificationPreferencesService);
		this.connectivityNotificationProducer = new ConnectivityNotificationProducer(runtimeBridge, notificationCenterService, notificationPreferencesService);
		this.systemUpdateNotificationProducer = new SystemUpdateNotificationProducer(notificationCenterService, notificationPreferencesService);
		this.updatePollingService = new UpdatePollingService(updateLauncher, this::onUpdateCheckCompleted);
	}

	private NotificationCenterService createNotificationCenterService() {
		boolean seedDemoNotifications = Boolean.getBoolean("shale.notifications.seedDemo");
		if (seedDemoNotifications) {
			return NotificationCenterService.seeded(Clock.systemUTC());
		}
		return NotificationCenterService.empty();
	}

	public void showLogin() {
		notificationStartupGeneration.incrementAndGet();
		Future<?> startupFuture = notificationStartupFuture;
		if (startupFuture != null) {
			startupFuture.cancel(true);
			notificationStartupFuture = null;
		}
		liveUpdateNotificationBridge.stop();
		connectivityNotificationProducer.stop();
		taskDueDateNotificationGenerator.stop();
		updatePollingService.stop();
		notificationCenterService.clearAll();
		var root = load("/fxml/login.fxml", controller ->
		{
			LoginController c = (LoginController) controller;
			c.init(this, appState, authService, runtimeBridge, updateLauncher);
			return c;
		});
		setScene(root, "Shale — Sign in");
	}

	public void showMain() {
		long showMainStartNanos = System.nanoTime();
		System.out.println("[StartupTiming] showMain entry");
		var root = load("/fxml/main.fxml", controller ->
		{
			MainController c = (MainController) controller;
			c.init(this, appState, runtimeBridge, notificationCenterService);
			c.setUpdateLauncher(updateLauncher);
			return c;
		});
		setScene(root, "Shale");
		Platform.runLater(() -> System.out.println("[StartupTiming] main shell visible"));
		notificationPreferencesService.refreshActivePreferences();
		liveUpdateNotificationBridge.start();
		connectivityNotificationProducer.start();
		taskDueDateNotificationGenerator.start();
		updatePollingService.start();
		startNotificationBootstrapAsync();
		System.out.println("[Navigation] Initial route reset -> MY_SHALE");
		navigationManager.resetTo(AppRoute.myShale());
		showRouteInternal(AppRoute.myShale());
		notifyBackAvailabilityChanged();
		long showMainEndMs = (System.nanoTime() - showMainStartNanos) / 1_000_000;
		System.out.println("[StartupTiming] showMain critical path complete in " + showMainEndMs + " ms");
	}

	private void startNotificationBootstrapAsync() {
		Integer shaleClientId = appState.getShaleClientId();
		Integer userId = appState.getUserId();
		if (shaleClientId == null || shaleClientId <= 0 || userId == null || userId <= 0) {
			return;
		}
		long generation = notificationStartupGeneration.incrementAndGet();
		notificationStartupFuture = notificationStartupExecutor.submit(() -> {
			long bootstrapStartNanos = System.nanoTime();
			System.out.println("[StartupTiming] notification bootstrap start");
			try {
				long dueStartNanos = System.nanoTime();
				taskDueDateNotificationGenerator.runOnce();
				long dueElapsedMs = (System.nanoTime() - dueStartNanos) / 1_000_000;
				System.out.println("[StartupTiming] due-date initial pass complete in " + dueElapsedMs + " ms");

				long hydrateStartNanos = System.nanoTime();
				var unread = durableNotificationService.listUnread(shaleClientId, userId);
				long hydrateElapsedMs = (System.nanoTime() - hydrateStartNanos) / 1_000_000;
				System.out.println("[StartupTiming] durable notification load complete in " + hydrateElapsedMs + " ms");

				if (!isActiveSession(generation, shaleClientId, userId)) {
					System.out.println("[StartupTiming] notification bootstrap discarded (session changed)");
					return;
				}
				Platform.runLater(() -> {
					if (!isActiveSession(generation, shaleClientId, userId)) {
						System.out.println("[StartupTiming] notification UI apply skipped (session changed)");
						return;
					}
					durableNotificationService.pushLoaded(notificationCenterService, unread);
					long bootstrapElapsedMs = (System.nanoTime() - bootstrapStartNanos) / 1_000_000;
					System.out.println("[StartupTiming] notification bootstrap applied in " + bootstrapElapsedMs + " ms");
				});
			} catch (RuntimeException ex) {
				System.err.println("[StartupTiming] notification bootstrap failed: " + ex.getMessage());
				ex.printStackTrace(System.err);
			}
		});
	}

	private boolean isActiveSession(long generation, int expectedShaleClientId, int expectedUserId) {
		Integer currentShaleClientId = appState.getShaleClientId();
		Integer currentUserId = appState.getUserId();
		return generation == notificationStartupGeneration.get()
				&& currentShaleClientId != null
				&& currentUserId != null
				&& currentShaleClientId == expectedShaleClientId
				&& currentUserId == expectedUserId;
	}


	public void onUpdateCheckCompleted(UpdateCheckResult result) {
		if (Platform.isFxApplicationThread()) {
			systemUpdateNotificationProducer.onUpdateCheckResult(result);
			return;
		}
		Platform.runLater(() -> systemUpdateNotificationProducer.onUpdateCheckResult(result));
	}

	public void onUpdaterLaunchSucceeded() {
		systemUpdateNotificationProducer.onUpdaterLaunchSucceeded();
	}

	public boolean canGoBack() {
		return navigationManager.canGoBack();
	}

	public void goBack() {
		navigationManager.popBackDestination().ifPresentOrElse(route ->
		{
			System.out.println("[Navigation] Back destination -> " + route);
			showRouteInternal(route);
			notifyBackAvailabilityChanged();
		}, () ->
		{
			System.out.println("[Navigation] Back ignored; stack is empty.");
			notifyBackAvailabilityChanged();
		});
	}

	public void openMyShaleView() {
		navigateTo(AppRoute.myShale(), true);
	}

	public void openCasesListView() {
		navigateTo(AppRoute.casesList(), true);
	}

	public void openContactsListView() {
		navigateTo(AppRoute.contactsList(), true);
	}

	public void openOrganizationsListView() {
		navigateTo(AppRoute.organizationsList(), true);
	}

	public void openTeamListView() {
		navigateTo(AppRoute.teamList(), true);
	}

	public void openSettingsView() {
		navigateTo(AppRoute.settings(), true);
	}

	public void openSearchView(String query) {
		navigateTo(AppRoute.search(query), true);
	}

	public void openCaseProfile(Integer caseId, String sectionKey) {
		if (caseId == null || caseId <= 0) {
			System.err.println("Ignoring case navigation for invalid caseId: " + caseId);
			return;
		}
		navigateTo(AppRoute.caseProfile(caseId, sectionKey == null ? "OVERVIEW" : sectionKey), true);
	}

	private void recordCaseSectionNavigation(Integer caseId, String sectionKey) {
		if (caseId == null || caseId <= 0 || sectionKey == null || sectionKey.isBlank()) {
			return;
		}
		AppRoute destination = AppRoute.caseProfile(caseId, sectionKey);
		boolean recorded = navigationManager.recordNavigation(destination);
		if (!recorded) {
			return;
		}
		System.out.println("[Navigation] Route push (section only) -> " + destination);
		notifyBackAvailabilityChanged();
	}

	public void openOrganizationProfile(Integer organizationId) {
		if (organizationId == null || organizationId <= 0) {
			System.err.println("Ignoring organization navigation for invalid organizationId: " + organizationId);
			return;
		}
		navigateTo(AppRoute.organizationProfile(organizationId), true);
	}

	public void openUserProfile(Integer userId) {
		System.out.println("[TRACE ASSIGNED_CASES][SceneManager.openUserProfile] selectedUserId=" + userId);
		if (userId == null || userId <= 0) {
			System.err.println("Ignoring user navigation for invalid userId: " + userId);
			return;
		}
		navigateTo(AppRoute.userProfile(userId), true);
	}

	public void openContactProfile(Integer contactId) {
		if (contactId == null || contactId <= 0) {
			System.err.println("Ignoring contact navigation for invalid contactId: " + contactId);
			return;
		}
		navigateTo(AppRoute.contactProfile(contactId), true);
	}

	private void navigateTo(AppRoute route, boolean addToHistory) {
		Objects.requireNonNull(route, "route");
		String navContext = routePerfContext(route);
		PerfLog.log("NAV", "start", "page=" + route.type().name().toLowerCase() + navContext);
		long navStartNanos = PerfLog.start();
		if (addToHistory) {
			boolean recorded = navigationManager.recordNavigation(route);
			if (!recorded) {
				System.out.println("[Navigation] Ignored duplicate route: " + route);
				return;
			}
			System.out.println("[Navigation] Route push -> " + route);
		} else {
			navigationManager.resetTo(route);
			System.out.println("[Navigation] Route reset -> " + route);
		}

		showRouteInternal(route);
		notifyBackAvailabilityChanged();
		PerfLog.logDone("NAV", "ready page=" + route.type().name().toLowerCase() + navContext, navStartNanos);
	}

	private void showRouteInternal(AppRoute route) {
		MainController mainController = resolveMainController();
		PerfLog.log("CTRL", "start", "route=" + route.type().name().toLowerCase() + routePerfContext(route));
		if (mainController == null) {
			System.err.println("Unable to navigate; main controller is unavailable for route " + route);
			return;
		}

		try {
			switch (route.type()) {
			case MY_SHALE -> mainController.showMyShaleView();
			case CASES_LIST -> mainController.showCasesListView();
			case CONTACTS_LIST -> mainController.showContactsListView();
			case ORGANIZATIONS_LIST -> mainController.showOrganizationsListView();
			case TEAM_LIST -> mainController.showTeamListView();
			case SETTINGS -> mainController.showSettingsView();
			case SEARCH -> mainController.showSearchResultsView(route.searchQuery() == null ? "" : route.searchQuery());
			case CASE_PROFILE -> mainController.showCaseProfileView(route.entityId(), route.sectionKey());
			case CONTACT_PROFILE -> {
				Parent contactRoot = createContactView(
						route.entityId(),
						caseId ->
						{
							System.out.println("[Navigation] Rewired contact->case callback via SceneManager.openCaseProfile");
							openCaseProfile(caseId, "OVERVIEW");
						},
						() ->
						{
							System.out.println("[Navigation] Rewired contact delete/list callback via SceneManager.openContactsListView");
							openContactsListView();
						});
				mainController.showContactView(route.entityId(), contactRoot);
			}
			case ORGANIZATION_PROFILE -> {
				Parent organizationRoot = createOrganizationView(
						route.entityId(),
						caseId -> openCaseProfile(caseId, "OVERVIEW"),
						this::openOrganizationsListView);
				mainController.showOrganizationProfileView(route.entityId(), organizationRoot);
			}
			case USER_PROFILE -> {
				Parent userRoot = createUserView(route.entityId());
				mainController.showUserView(route.entityId(), userRoot);
			}
			default -> System.err.println("Unhandled route: " + route);
			}
		} catch (RuntimeException ex) {
			logRouteFailure(route, ex);
		}
	}

	private void logRouteFailure(AppRoute route, RuntimeException ex) {
		System.err.println("Failed to open route " + route + ": " + ex.getMessage());
		if (ex != null) {
			ex.printStackTrace(System.err);
		}

		Throwable current = ex == null ? null : ex.getCause();
		int depth = 1;
		while (current != null && depth <= 16) {
			System.err.println("  Cause[" + depth + "]: " + current.getClass().getName() + ": " + current.getMessage());
			if (current instanceof LoadException) {
				String loadMessage = current.getMessage();
				if (loadMessage != null && !loadMessage.isBlank()) {
					System.err.println("  FXML LoadException detail: " + loadMessage);
				}
			}
			current = current.getCause();
			depth++;
		}
	}

	private void notifyBackAvailabilityChanged() {
		MainController mainController = resolveMainController();
		if (mainController != null) {
			mainController.updateBackButtonState(canGoBack());
		}
	}

	/** Backwards-compatible: no callback. */
	public Parent createCasesView() {
		return createCasesView(null);
	}

	/**
	 * Create the Cases view and optionally provide a callback that the CasesController can
	 * invoke when a case card is clicked (open case).
	 */
	public Parent createCasesView(Consumer<Integer> onOpenCase) {
		return load("/fxml/cases.fxml", controller ->
		{
			CasesController c = (CasesController) controller;

			CaseDao caseDao = new CaseDao(dbSessionProvider);
			c.init(appState, runtimeBridge, caseDao, onOpenCase);
			return c;
		});
	}

	public Parent createOrganizationsView(Consumer<Integer> onOpenOrganization) {
		return load("/fxml/organizations.fxml", controller ->
		{
			OrganizationsController c = (OrganizationsController) controller;
			OrganizationDao organizationDao = new OrganizationDao(dbSessionProvider);
			c.init(appState, runtimeBridge, organizationDao, onOpenOrganization, this);
			return c;
		});
	}

	public Parent createContactsView(Consumer<Integer> onOpenContact) {
		return load("/fxml/contacts.fxml", controller ->
		{
			ContactsController c = (ContactsController) controller;
			ContactDao contactDao = new ContactDao(dbSessionProvider);
			c.init(appState, contactDao, onOpenContact);
			return c;
		});
	}

	public Parent createTeamView(Consumer<Integer> onOpenUser) {
		return load("/fxml/team.fxml", controller ->
		{
			TeamController c = (TeamController) controller;
			UserDao userDao = new UserDao(dbSessionProvider);
			c.init(appState, userDao, onOpenUser);
			return c;
		});
	}

	public Parent createSettingsView() {
		return load("/fxml/settings.fxml", controller ->
		{
			SettingsController c = (SettingsController) controller;
			c.init(notificationPreferencesService);
			return c;
		});
	}

	public Parent createSearchView(String query,
			Consumer<Integer> onOpenCase,
			Consumer<Integer> onOpenContact,
			Consumer<Integer> onOpenOrganization,
			Consumer<Integer> onOpenUser) {
		return load("/fxml/search.fxml", controller ->
		{
			SearchController c = (SearchController) controller;
			CaseDao caseDao = new CaseDao(dbSessionProvider);
			SearchService searchService = new SearchService(
					caseDao,
					new ContactDao(dbSessionProvider),
					new OrganizationDao(dbSessionProvider),
					new UserDao(dbSessionProvider));
			CaseDetailService caseDetailService = new CaseDetailService(caseDao, appState);
			c.init(appState, searchService, caseDetailService, runtimeBridge, query, onOpenCase, onOpenContact, onOpenOrganization, onOpenUser);
			return c;
		});
	}

	private String routePerfContext(AppRoute route) {
		if (route == null) {
			return "";
		}
		Integer entityId = route.entityId();
		return switch (route.type()) {
			case CASE_PROFILE -> entityId == null ? "" : " caseId=" + entityId;
			case USER_PROFILE -> entityId == null ? "" : " userId=" + entityId;
			case ORGANIZATION_PROFILE -> entityId == null ? "" : " organizationId=" + entityId;
			case CONTACT_PROFILE -> entityId == null ? "" : " contactId=" + entityId;
			default -> "";
		};
	}

	private static final String ROOT_CONTROLLER_KEY = "sceneManager.controller";

	public Parent createUserView(int userId) {
		return load("/fxml/user.fxml", controller ->
		{
			UserController c = (UserController) controller;
			UserDao userDao = new UserDao(dbSessionProvider);
			CaseDao caseDao = new CaseDao(dbSessionProvider);
			TaskDao taskDao = new TaskDao(dbSessionProvider);
			NotificationDao notificationDao = new NotificationDao(dbSessionProvider);
			CaseTaskService caseTaskService = new CaseTaskService(taskDao, userDao, runtimeBridge, notificationDao);
			UserDetailService userDetailService = new UserDetailService(userDao, caseDao, taskDao);
			c.init(userId, userDetailService, appState, runtimeBridge, relatedCaseId ->
			{
				System.out.println("[Navigation] Rewired user related-case callback via SceneManager.openCaseProfile");
				openCaseProfile(relatedCaseId, "OVERVIEW");
			}, this::openUserProfile, caseTaskService);
			return c;
		});
	}

	public Parent createContactView(int contactId, Consumer<Integer> onOpenCase, Runnable onContactDeleted) {
		return load("/fxml/contact.fxml", controller ->
		{
			ContactViewController c = (ContactViewController) controller;
			ContactDao contactDao = new ContactDao(dbSessionProvider);
			ContactDetailService contactDetailService = new ContactDetailService(contactDao);
			c.init(contactId, contactDetailService, appState, onOpenCase, onContactDeleted);
			return c;
		});
	}

	public Parent createContactView(int contactId, Consumer<Integer> onOpenCase) {
		return createContactView(contactId, onOpenCase, null);
	}

	public Parent createContactView(int contactId) {
		return createContactView(contactId, null, null);
	}

	public Parent createOrganizationView(int organizationId, Consumer<Integer> onOpenCase, Runnable onOrganizationDeleted) {
		return load("/fxml/organization.fxml", controller ->
		{
			OrganizationController c = (OrganizationController) controller;
			OrganizationDao organizationDao = new OrganizationDao(dbSessionProvider);
			c.init(organizationId, organizationDao, appState, runtimeBridge, onOpenCase, onOrganizationDeleted);
			return c;
		});
	}

	public Parent createMyShaleView(Consumer<Integer> onOpenCase, Consumer<Integer> onOpenUser) {
		return load("/fxml/my-shale.fxml", controller ->
		{
			MyShaleController c = (MyShaleController) controller;
			CaseDao caseDao = new CaseDao(dbSessionProvider);
			TaskDao taskDao = new TaskDao(dbSessionProvider);
			UserDao userDao = new UserDao(dbSessionProvider);
			NotificationDao notificationDao = new NotificationDao(dbSessionProvider);
			CaseTaskService caseTaskService = new CaseTaskService(taskDao, userDao, runtimeBridge, notificationDao);
			c.init(appState, runtimeBridge, caseDao, caseTaskService, onOpenCase, onOpenUser);
			return c;
		});
	}

	public Parent createCaseView(int caseId, String sectionKey, Consumer<Integer> onOpenOrganization, Runnable onCaseDeleted) {
		return load("/fxml/case.fxml", controller ->
		{
			CaseController c = (CaseController) controller;
			CaseDao caseDao = new CaseDao(dbSessionProvider);
			OrganizationDao organizationDao = new OrganizationDao(dbSessionProvider);
			ContactDao contactDao = new ContactDao(dbSessionProvider);
			CaseDetailService caseDetailService = new CaseDetailService(caseDao, appState);
			TaskDao taskDao = new TaskDao(dbSessionProvider);
			UserDao userDao = new UserDao(dbSessionProvider);
			NotificationDao notificationDao = new NotificationDao(dbSessionProvider);
			CaseTaskService caseTaskService = new CaseTaskService(taskDao, userDao, runtimeBridge, notificationDao);
			c.init(caseId, caseDao, caseDetailService, caseTaskService, organizationDao, contactDao, appState, runtimeBridge, onCaseDeleted);
			c.setInitialSection(sectionKey);
			c.setOnOpenUser(this::openUserProfile);
			c.setOnOpenStatus(this::openStatusProfile);
			c.setOnOpenContact(this::openContactProfile);
			c.setOnOpenCase(relatedCaseId ->
			{
				System.out.println("[Navigation] Rewired case related-case callback via SceneManager.openCaseProfile");
				openCaseProfile(relatedCaseId, "OVERVIEW");
			});
			c.setOnSectionNavigation(selectedSectionKey ->
			{
				if (selectedSectionKey == null) {
					return;
				}
				recordCaseSectionNavigation(caseId, selectedSectionKey);
			});
			c.setOnOpenTask(this::openTaskProfile);
			c.setOnOpenOrganization(onOpenOrganization);
			return c;
		});
	}

	public Parent createCaseView(int caseId, Consumer<Integer> onOpenOrganization, Runnable onCaseDeleted) {
		return createCaseView(caseId, "OVERVIEW", onOpenOrganization, onCaseDeleted);
	}

	public Parent createCaseView(int caseId, Consumer<Integer> onOpenOrganization) {
		return createCaseView(caseId, "OVERVIEW", onOpenOrganization, null);
	}

	public void showNewOrganizationDialog(Consumer<Integer> onOrganizationCreated) {
		try {
			URL url = Objects.requireNonNull(getClass().getResource("/fxml/new-organization.fxml"), "Missing FXML: /fxml/new-organization.fxml");
			FXMLLoader loader = new FXMLLoader(url);
			Parent root = loader.load();

			Stage dialog = new Stage();
			AppDialogs.applySecondaryWindowChrome(dialog);
			dialog.initOwner(stage);
			dialog.initModality(Modality.WINDOW_MODAL);
			dialog.setTitle("New Organization");

			NewOrganizationController controller = loader.getController();
			OrganizationDao organizationDao = new OrganizationDao(dbSessionProvider);
			controller.init(appState, organizationDao, dialog, onOrganizationCreated);

			VBox dialogRoot = new VBox(
					AppDialogs.createSecondaryWindowHeader(dialog, "New Organization", dialog::close),
					root);
			dialogRoot.getStyleClass().add("secondary-window-shell");
			VBox.setVgrow(root, Priority.ALWAYS);

			Scene dialogScene = new Scene(dialogRoot);
			dialogScene.getStylesheets().add(Objects.requireNonNull(
					getClass().getResource("/css/app.css")).toExternalForm());
			dialog.setScene(dialogScene);
			dialog.setMinWidth(760);
			dialog.setMinHeight(720);
			dialog.showAndWait();
		} catch (IOException e) {
			throw new RuntimeException("Failed to open New Organization dialog", e);
		}
	}

	public void showNewIntakeDialog(Consumer<Integer> onCaseCreated) {
		try {
			URL url = Objects.requireNonNull(getClass().getResource("/fxml/new-intake.fxml"), "Missing FXML: /fxml/new-intake.fxml");
			FXMLLoader loader = new FXMLLoader(url);
			Parent root = loader.load();

			Stage dialog = new Stage();
			AppDialogs.applySecondaryWindowChrome(dialog);
			dialog.initOwner(stage);
			dialog.initModality(Modality.WINDOW_MODAL);
			dialog.setTitle("New Intake");

			NewIntakeController controller = loader.getController();
			CaseDao caseDao = new CaseDao(dbSessionProvider);
			OrganizationDao organizationDao = new OrganizationDao(dbSessionProvider);
			controller.init(appState, caseDao, organizationDao, dialog, onCaseCreated);

			VBox dialogRoot = new VBox(
					AppDialogs.createSecondaryWindowHeader(dialog, "New Intake", dialog::close),
					root);
			dialogRoot.getStyleClass().add("secondary-window-shell");
			VBox.setVgrow(root, Priority.ALWAYS);

			Scene dialogScene = new Scene(dialogRoot);
			dialogScene.getStylesheets().add(Objects.requireNonNull(
					getClass().getResource("/css/app.css")).toExternalForm());
			dialog.setScene(dialogScene);
			dialog.setWidth(1180);
			dialog.setMinWidth(1080);
			dialog.setMinHeight(760);
			dialog.showAndWait();
		} catch (IOException e) {
			throw new RuntimeException("Failed to open New Intake dialog", e);
		}
	}

	private void openStatusProfile(Integer statusId) {
		System.out.println("Navigate to Status: " + statusId);
	}

	public void openTaskProfile(Long taskId) {
		if (taskId == null || taskId <= 0) {
			System.err.println("Ignoring task navigation for invalid taskId: " + taskId);
			return;
		}
		Integer shaleClientId = appState.getShaleClientId();
		Integer currentUserId = appState.getUserId();
		if (shaleClientId == null || shaleClientId <= 0 || currentUserId == null || currentUserId <= 0) {
			AppDialogs.showError(stage, "Tasks", "You must be signed in to view task details.");
			return;
		}
		CaseTaskService caseTaskService = new CaseTaskService(
				new TaskDao(dbSessionProvider),
				new UserDao(dbSessionProvider),
				runtimeBridge,
				new NotificationDao(dbSessionProvider));
		new Thread(() -> loadAndOpenTaskDialog(taskId, shaleClientId, currentUserId, caseTaskService),
				"scene-manager-open-task-" + taskId).start();
	}

	private void loadAndOpenTaskDialog(Long taskId, int shaleClientId, int currentUserId, CaseTaskService caseTaskService) {
		try {
			TaskDetailDto detail = caseTaskService.loadTaskDetail(taskId, shaleClientId);
			List<TaskStatusOptionDto> statuses = caseTaskService.loadActiveTaskStatuses(shaleClientId);
			List<TaskPriorityOptionDto> priorities = caseTaskService.loadActivePriorities(shaleClientId);
			Platform.runLater(() -> showTaskDetailDialog(taskId, shaleClientId, currentUserId, caseTaskService, detail, statuses, priorities));
		} catch (Exception ex) {
			Platform.runLater(() -> AppDialogs.showError(stage, "Tasks", "Failed to load task details. " + rootCauseMessage(ex)));
		}
	}

	private void showTaskDetailDialog(
			long taskId,
			int shaleClientId,
			int currentUserId,
			CaseTaskService caseTaskService,
			TaskDetailDto detail,
			List<TaskStatusOptionDto> statuses,
			List<TaskPriorityOptionDto> priorities) {
		if (detail == null) {
			AppDialogs.showError(stage, "Tasks", "Task was not found or may have been deleted.");
			return;
		}
		List<CaseTaskService.AssignedTaskUserOption> assignedTeam =
				caseTaskService.loadAssignedUsersForTask(detail.id(), shaleClientId);
		List<TaskDetailDialog.TaskActivityEntry> activityEntries = caseTaskService.loadTaskActivity(detail.id(), shaleClientId).stream()
				.map(item -> new TaskDetailDialog.TaskActivityEntry(
						item.title(),
						item.body(),
						item.actorDisplayName(),
						item.occurredAt()))
				.toList();
		List<TaskDetailDialog.TaskNoteEntry> noteEntries = caseTaskService.loadTaskNotes(detail.id(), shaleClientId).stream()
				.map(note -> new TaskDetailDialog.TaskNoteEntry(
						note.id(),
						note.userId(),
						note.userDisplayName(),
						note.body(),
						note.createdAt(),
						note.updatedAt(),
						note.userId() == currentUserId))
				.toList();
		TaskDetailDialog.TaskDetailModel model = new TaskDetailDialog.TaskDetailModel(
				detail.id(),
				detail.caseId(),
				detail.caseName(),
				detail.caseResponsibleAttorney(),
				detail.caseResponsibleAttorneyColor(),
				detail.caseNonEngagementLetterSent(),
				detail.title(),
				detail.description(),
				detail.dueAt(),
				detail.statusId(),
				detail.priorityId(),
					detail.createdByDisplayName(),
						assignedTeam.stream()
								.map(member -> new TaskDetailDialog.AssignedTeamMember(
										member.userId(),
										member.displayName(),
										member.color()))
							.toList(),
					activityEntries,
					noteEntries,
				detail.completedAt() != null);
		Window owner = stage.getScene() == null ? stage : stage.getScene().getWindow();
		var result = TaskDetailDialog.showAndWait(
				"SCENE_MANAGER",
				0L,
				owner,
				model,
				statuses,
				priorities,
				null,
				id -> caseTaskService.loadAssignableUsersForTask(id, shaleClientId),
				id -> caseTaskService.loadAssignedUsersForTask(id, shaleClientId).stream()
						.map(member -> new TaskDetailDialog.AssignedTeamMember(
								member.userId(),
								member.displayName(),
								member.color()))
						.toList(),
				id -> caseTaskService.loadTaskActivity(id, shaleClientId).stream()
						.map(item -> new TaskDetailDialog.TaskActivityEntry(
								item.title(),
								item.body(),
								item.actorDisplayName(),
								item.occurredAt()))
						.toList(),
				id -> caseTaskService.loadTaskNotes(id, shaleClientId).stream()
						.map(note -> new TaskDetailDialog.TaskNoteEntry(
								note.id(),
								note.userId(),
								note.userDisplayName(),
								note.body(),
								note.createdAt(),
								note.updatedAt(),
								note.userId() == currentUserId))
						.toList(),
				new TaskDetailDialog.AssignmentEditor() {
					@Override
					public List<TaskDetailDialog.AssignedTeamMember> addAndReload(int userId) {
						caseTaskService.addTaskAssignment(model.taskId(), shaleClientId, userId, currentUserId);
						return caseTaskService.loadAssignedUsersForTask(model.taskId(), shaleClientId).stream()
								.map(member -> new TaskDetailDialog.AssignedTeamMember(
										member.userId(),
										member.displayName(),
										member.color()))
								.toList();
					}

					@Override
					public List<TaskDetailDialog.AssignedTeamMember> removeAndReload(int userId) {
						caseTaskService.removeTaskAssignment(model.taskId(), shaleClientId, userId, currentUserId);
						return caseTaskService.loadAssignedUsersForTask(model.taskId(), shaleClientId).stream()
								.map(member -> new TaskDetailDialog.AssignedTeamMember(
										member.userId(),
										member.displayName(),
										member.color()))
								.toList();
					}
				},
				new TaskDetailDialog.NotesEditor() {
					@Override
					public List<TaskDetailDialog.TaskNoteEntry> addAndReload(String body) {
						caseTaskService.addTaskNote(model.taskId(), shaleClientId, currentUserId, body);
						return caseTaskService.loadTaskNotes(model.taskId(), shaleClientId).stream()
								.map(note -> new TaskDetailDialog.TaskNoteEntry(
										note.id(),
										note.userId(),
										note.userDisplayName(),
										note.body(),
										note.createdAt(),
										note.updatedAt(),
										note.userId() == currentUserId))
								.toList();
					}

					@Override
					public List<TaskDetailDialog.TaskNoteEntry> editAndReload(long noteId, String body) {
						caseTaskService.updateTaskNote(noteId, shaleClientId, currentUserId, body);
						return caseTaskService.loadTaskNotes(model.taskId(), shaleClientId).stream()
								.map(note -> new TaskDetailDialog.TaskNoteEntry(
										note.id(),
										note.userId(),
										note.userDisplayName(),
										note.body(),
										note.createdAt(),
										note.updatedAt(),
										note.userId() == currentUserId))
								.toList();
					}
				},
				this::openUserProfile,
				caseId -> openCaseProfile(caseId, "OVERVIEW"));
		if (result.isEmpty()) {
			return;
		}
		TaskDetailDialog.TaskDetailResult action = result.get();
		if (action.action() == TaskDetailDialog.TaskDetailAction.DELETE) {
			new Thread(() -> {
				try {
					caseTaskService.deleteTask(taskId, shaleClientId, currentUserId);
				} catch (Exception ex) {
					Platform.runLater(() -> AppDialogs.showError(stage, "Tasks", "Failed to delete task. " + rootCauseMessage(ex)));
				}
			}, "scene-manager-delete-task-" + taskId).start();
			return;
		}
		TaskDetailDialog.SaveTaskPayload payload = action.payload();
		if (payload == null) {
			return;
		}
		CaseTaskService.UpdateTaskRequest request = new CaseTaskService.UpdateTaskRequest(
				taskId,
				shaleClientId,
				payload.title(),
				payload.description(),
				payload.dueAt(),
				payload.statusId(),
				payload.priorityId(),
				payload.completed(),
				currentUserId);
		new Thread(() -> {
			try {
				caseTaskService.updateTask(request);
			} catch (Exception ex) {
				Platform.runLater(() -> AppDialogs.showError(stage, "Tasks", "Failed to save task. " + rootCauseMessage(ex)));
			}
		}, "scene-manager-save-task-" + taskId).start();
	}

	private static String rootCauseMessage(Throwable throwable) {
		if (throwable == null) {
			return "";
		}
		Throwable current = throwable;
		while (current.getCause() != null && current.getCause() != current) {
			current = current.getCause();
		}
		String message = current.getMessage();
		return (message == null || message.isBlank()) ? "" : "Details: " + message;
	}

	private MainController resolveMainController() {
		Scene scene = stage.getScene();
		if (scene == null) {
			return null;
		}

		Parent root = scene.getRoot();
		if (root == null) {
			return null;
		}

		Object controller = root.getProperties().get(ROOT_CONTROLLER_KEY);
		if (controller instanceof MainController mainController) {
			return mainController;
		}

		return null;
	}

	private Parent load(String fxmlPath, Function<Object, Object> controllerConfigurer) {
		try {
			URL url = Objects.requireNonNull(getClass().getResource(fxmlPath), "Missing FXML: " + fxmlPath);
			FXMLLoader loader = new FXMLLoader(url);

			loader.setControllerFactory(clz ->
			{
				try {
					Object controller = clz.getDeclaredConstructor().newInstance();
					return controllerConfigurer.apply(controller);
				} catch (Exception e) {
					throw new RuntimeException("Controller init failed for " + clz.getName(), e);
				}
			});

			Parent root = loader.load();
			root.getProperties().put(ROOT_CONTROLLER_KEY, loader.getController());
			return root;
		} catch (IOException e) {
			throw new RuntimeException("Failed to load FXML: " + fxmlPath, e);
		}
	}

	private void setScene(Parent root, String title) {
		Scene scene = stage.getScene();
		if (scene == null) {
			scene = new Scene(root, 1650, 900);
			scene.getStylesheets().add(Objects.requireNonNull(
					getClass().getResource("/css/app.css")).toExternalForm());
			stage.setScene(scene);
		} else {
			scene.setRoot(root);
		}
		stage.setTitle(title);
		stage.show();
	}

	public void showError(String message) {
		System.out.println("*******************SceneManager.showError() " + message);
	}
}
