package com.shale.ui.navigation;

import com.shale.core.runtime.DbSessionProvider;
import com.shale.core.dto.TaskDetailDto;
import com.shale.core.dto.TaskPriorityOptionDto;
import com.shale.core.dto.TaskStatusOptionDto;
import com.shale.data.dao.CalendarEventDao;
import com.shale.data.dao.CalendarEventTypeDao;
import com.shale.data.dao.CalendarFeedDao;
import com.shale.data.dao.CaseDao;
import com.shale.data.dao.MaterialRequestDao;
import com.shale.data.service.adapter.CaseServiceAdapter;
import com.shale.data.service.adapter.MaterialRequestServiceAdapter;
import com.shale.data.dao.ContactDao;
import com.shale.data.dao.OrganizationDao;
import com.shale.data.dao.UserDao;
import com.shale.data.dao.TaskDao;
import com.shale.data.dao.NotificationDao;
import com.shale.data.dao.UserBoardLanePreferencesDao;
import com.shale.data.dao.UserPreferencesDao;
import com.shale.data.dao.AuditLogDao;
import com.shale.ui.services.CalendarService;
import com.shale.ui.controller.CaseController;
import com.shale.ui.controller.CasesController;
import com.shale.ui.controller.CalendarController;
import com.shale.ui.controller.ContactViewController;
import com.shale.ui.controller.ContactsController;
import com.shale.ui.controller.AuditLogViewerController;
import com.shale.ui.controller.LoginController;
import com.shale.ui.controller.MainController;
import com.shale.ui.controller.MyShaleController;
import com.shale.ui.controller.NewIntakeController;
import com.shale.ui.controller.NewOrganizationController;
import com.shale.ui.controller.OrganizationController;
import com.shale.ui.controller.OrganizationsController;
import com.shale.ui.controller.SearchController;
import com.shale.ui.controller.ReportsController;
import com.shale.ui.controller.SettingsController;
import com.shale.ui.controller.TeamController;
import com.shale.ui.controller.UserController;
import com.shale.ui.component.dialog.AppDialogs;
import com.shale.ui.component.dialog.TaskDetailDialog;
import com.shale.ui.services.CaseDetailService;
import com.shale.ui.services.ContactDetailService;
import com.shale.ui.services.CaseTaskService;
import com.shale.ui.services.CaseExportService;
import com.shale.ui.services.SearchService;
import com.shale.ui.services.UserDetailService;
import com.shale.ui.services.UiAuthService;
import com.shale.ui.services.UiRuntimeBridge;
import com.shale.ui.services.UserPreferencesService;
import com.shale.ui.services.UpdatePollingService;
import com.shale.ui.services.PhiReadAuditService;
import com.shale.ui.state.AppState;
import com.shale.ui.util.PerfLog;
import com.shale.ui.util.WindowSizingUtil;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.fxml.LoadException;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import com.shale.ui.notification.NotificationPollingService;
import com.shale.ui.notification.NoOpDesktopNotificationPresenter;
import com.shale.data.service.adapter.NotificationServiceAdapter;

public final class SceneManager {
	private static final Logger log = LoggerFactory.getLogger(SceneManager.class);

	private final AtomicBoolean taskDetailDialogInFlight = new AtomicBoolean(false);

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
	private CalendarController calendarController;
	private Integer pendingCalendarNotificationEventId;
	private final DurableNotificationService durableNotificationService;
	private final TaskDueDateNotificationGenerator taskDueDateNotificationGenerator;
	private final NotificationPollingService notificationPollingService;
	private final UpdatePollingService updatePollingService;
	private final PhiReadAuditService phiReadAuditService;
	private final ExecutorService notificationBadgeCountExecutor;
	private final ExecutorService notificationStartupExecutor;
	private final AtomicLong notificationStartupGeneration = new AtomicLong(0);
	private final AtomicLong notificationBadgeCountGeneration = new AtomicLong(0);
	private volatile Future<?> notificationBadgeCountFuture;
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
		this.notificationPollingService = new NotificationPollingService(
				new NotificationServiceAdapter(new NotificationDao(dbSessionProvider)), notificationCenterService,
				durableNotificationService, new NoOpDesktopNotificationPresenter(), Platform::runLater);
		this.taskDueDateNotificationGenerator = new TaskDueDateNotificationGenerator(
				new TaskDao(dbSessionProvider),
				new MaterialRequestDao(dbSessionProvider),
				new NotificationDao(dbSessionProvider),
				appState,
				notificationPreferencesService,
				new AssignedUserTaskDueNotificationRecipientResolver());
		this.notificationBadgeCountExecutor = Executors.newSingleThreadExecutor(r -> {
			Thread t = new Thread(r, "notification-badge-count-worker");
			t.setDaemon(true);
			return t;
		});
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
		this.phiReadAuditService = new PhiReadAuditService(new AuditLogDao(dbSessionProvider), appState);
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
		notificationBadgeCountGeneration.incrementAndGet();
		Future<?> badgeCountFuture = notificationBadgeCountFuture;
		if (badgeCountFuture != null) {
			badgeCountFuture.cancel(true);
			notificationBadgeCountFuture = null;
		}
		Future<?> startupFuture = notificationStartupFuture;
		if (startupFuture != null) {
			startupFuture.cancel(true);
			notificationStartupFuture = null;
		}
		liveUpdateNotificationBridge.stop();
		connectivityNotificationProducer.stop();
		taskDueDateNotificationGenerator.stop();
		notificationPollingService.stop();
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
		startNotificationBadgeCountAsync();
		notificationPreferencesService.refreshActivePreferences();
		liveUpdateNotificationBridge.start();
		connectivityNotificationProducer.start();
		taskDueDateNotificationGenerator.start();
		Integer pollingTenantId = appState.getShaleClientId();
		Integer pollingUserId = appState.getUserId();
		if (pollingTenantId != null && pollingTenantId > 0 && pollingUserId != null && pollingUserId > 0) {
			notificationPollingService.start(pollingTenantId, pollingUserId);
		}
		updatePollingService.start();
		startNotificationBootstrapAsync();
		System.out.println("[Navigation] Initial route reset -> MY_SHALE");
		navigationManager.resetTo(AppRoute.myShale());
		showRouteInternal(AppRoute.myShale());
		notifyBackAvailabilityChanged();
		long showMainEndMs = (System.nanoTime() - showMainStartNanos) / 1_000_000;
		System.out.println("[StartupTiming] showMain critical path complete in " + showMainEndMs + " ms");
	}

	private void startNotificationBadgeCountAsync() {
		Integer shaleClientId = appState.getShaleClientId();
		Integer userId = appState.getUserId();
		if (shaleClientId == null || shaleClientId <= 0 || userId == null || userId <= 0) {
			return;
		}
		long generation = notificationBadgeCountGeneration.incrementAndGet();
		Future<?> existingFuture = notificationBadgeCountFuture;
		if (existingFuture != null) {
			existingFuture.cancel(true);
		}
		notificationBadgeCountFuture = notificationBadgeCountExecutor.submit(() -> {
			long startNanos = System.nanoTime();
			PerfLog.debug(log, "PERF notifications.badge.count.start tenantId={} userId={} generation={}", shaleClientId, userId, generation);
			try {
				int unreadCount = durableNotificationService.countUnread(shaleClientId, userId);
				long queryElapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
				if (!isActiveBadgeSession(generation, shaleClientId, userId)) {
					PerfLog.debug(log, "PERF notifications.badge.count.discard tenantId={} userId={} generation={} unreadCount={} elapsedMs={}",
							shaleClientId, userId, generation, unreadCount, queryElapsedMs);
					return;
				}
				Platform.runLater(() -> {
					if (!isActiveBadgeSession(generation, shaleClientId, userId)) {
						PerfLog.debug(log, "PERF notifications.badge.count.applySkipped tenantId={} userId={} generation={} unreadCount={}",
								shaleClientId, userId, generation, unreadCount);
						return;
					}
					notificationCenterService.applyServerUnreadCount(unreadCount);
					long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
					PerfLog.debug(log, "PERF notifications.badge.count.done tenantId={} userId={} generation={} unreadCount={} elapsedMs={}",
							shaleClientId, userId, generation, unreadCount, elapsedMs);
				});
			} catch (RuntimeException ex) {
				log.error("Notification badge count load failed tenantId={} userId={} generation={}", shaleClientId, userId, generation, ex);
			}
		});
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
			PerfLog.debug(log, "PERF notifications.bootstrap.full.start tenantId={} userId={} generation={}", shaleClientId, userId, generation);
			try {
				long dueStartNanos = System.nanoTime();
				taskDueDateNotificationGenerator.runOnce();
				long dueElapsedMs = (System.nanoTime() - dueStartNanos) / 1_000_000;
				PerfLog.debug(log, "PERF notifications.bootstrap.dueDate.done tenantId={} userId={} generation={} elapsedMs={}",
						shaleClientId, userId, generation, dueElapsedMs);

				long hydrateStartNanos = System.nanoTime();
				var unread = durableNotificationService.listUnread(shaleClientId, userId);
				long hydrateElapsedMs = (System.nanoTime() - hydrateStartNanos) / 1_000_000;
				PerfLog.debug(log, "PERF notifications.bootstrap.hydrate.done tenantId={} userId={} generation={} count={} elapsedMs={}",
						shaleClientId, userId, generation, unread.size(), hydrateElapsedMs);

				if (!isActiveSession(generation, shaleClientId, userId)) {
					PerfLog.debug(log, "PERF notifications.bootstrap.full.discard tenantId={} userId={} generation={} reason=session_changed",
							shaleClientId, userId, generation);
					return;
				}
				Platform.runLater(() -> {
					if (!isActiveSession(generation, shaleClientId, userId)) {
						PerfLog.debug(log, "PERF notifications.bootstrap.full.applySkipped tenantId={} userId={} generation={} reason=session_changed",
								shaleClientId, userId, generation);
						return;
					}
					durableNotificationService.pushLoaded(notificationCenterService, unread);
					notificationCenterService.completeInitialHydration();
					notificationBadgeCountGeneration.incrementAndGet();
					long bootstrapElapsedMs = (System.nanoTime() - bootstrapStartNanos) / 1_000_000;
					PerfLog.debug(log, "PERF notifications.bootstrap.full.done tenantId={} userId={} generation={} hydratedCount={} elapsedMs={}",
							shaleClientId, userId, generation, unread.size(), bootstrapElapsedMs);
				});
			} catch (RuntimeException ex) {
				log.error("Notification full bootstrap failed tenantId={} userId={} generation={}", shaleClientId, userId, generation, ex);
			}
		});
	}

	private boolean isActiveBadgeSession(long generation, int expectedShaleClientId, int expectedUserId) {
		Integer currentShaleClientId = appState.getShaleClientId();
		Integer currentUserId = appState.getUserId();
		return generation == notificationBadgeCountGeneration.get()
				&& currentShaleClientId != null
				&& currentUserId != null
				&& currentShaleClientId == expectedShaleClientId
				&& currentUserId == expectedUserId;
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

	public void openCalendarView() {
		navigateTo(AppRoute.calendar(), true);
	}

	public void openReportsView() {
		navigateTo(AppRoute.reports(), true);
	}

	public void openCalendarEventFromNotification(long calendarEventId) {
		if (calendarEventId <= 0 || calendarEventId > Integer.MAX_VALUE) {
			return;
		}
		if (calendarController != null) {
			calendarController.openCalendarEventFromNotification(calendarEventId);
			return;
		}
		pendingCalendarNotificationEventId = (int) calendarEventId;
		openCalendarView();
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
			case CALENDAR -> mainController.showCalendarView();
			case REPORTS -> mainController.showReportsView();
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
			TaskDao taskDao = new TaskDao(dbSessionProvider);
			UserDao userDao = new UserDao(dbSessionProvider);
			NotificationDao notificationDao = new NotificationDao(dbSessionProvider);
			CalendarService calendarService = new CalendarService(new CalendarEventTypeDao(dbSessionProvider), new CalendarEventDao(dbSessionProvider), new CalendarFeedDao(dbSessionProvider), notificationDao, runtimeBridge);
			CaseTaskService caseTaskService = new CaseTaskService(taskDao, userDao, runtimeBridge, notificationDao);
			c.init(appState, runtimeBridge, caseDao, caseTaskService,
					new CaseExportService(caseDao, appState, phiReadAuditService), onOpenCase);
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

	public Parent createReportsView() {
		return load("/fxml/reports.fxml", controller ->
		{
			ReportsController c = (ReportsController) controller;
			CaseDao caseDao = new CaseDao(dbSessionProvider);
			c.init(appState, caseDao, new CaseExportService(caseDao, appState, phiReadAuditService));
			return c;
		});
	}

	public Parent createCalendarView() {
		return load("/fxml/calendar.fxml", controller -> {
			CalendarController c = (CalendarController) controller;
			this.calendarController = c;
			CalendarFeedDao calendarFeedDao = new CalendarFeedDao(dbSessionProvider);
			NotificationDao notificationDao = new NotificationDao(dbSessionProvider);
			CalendarService calendarService = new CalendarService(
					new CalendarEventTypeDao(dbSessionProvider),
					new CalendarEventDao(dbSessionProvider),
					calendarFeedDao,
					notificationDao,
					runtimeBridge);
			TaskDao taskDao = new TaskDao(dbSessionProvider);
			UserDao userDao = new UserDao(dbSessionProvider);
			CaseDao caseDao = new CaseDao(dbSessionProvider);
			CaseTaskService caseTaskService = new CaseTaskService(taskDao, userDao, runtimeBridge, notificationDao);
			c.init(appState, calendarService, calendarFeedDao, caseTaskService, caseDao, caseId -> openCaseProfile(caseId, "OVERVIEW"), taskId -> openTaskProfile(taskId, c::refreshCurrentRange));
			Integer pendingEventId = pendingCalendarNotificationEventId;
			if (pendingEventId != null && pendingEventId > 0) {
				pendingCalendarNotificationEventId = null;
				Platform.runLater(() -> c.openCalendarEventFromNotification(pendingEventId.longValue()));
			}
			return c;
		});
	}

	public Parent createSettingsView() {
		return load("/fxml/settings.fxml", controller ->
		{
			SettingsController c = (SettingsController) controller;
			c.init(notificationPreferencesService, appState, this::showAuditLogViewer, new CaseServiceAdapter(new CaseDao(dbSessionProvider)), new MaterialRequestServiceAdapter(new MaterialRequestDao(dbSessionProvider)), new UserDao(dbSessionProvider), runtimeBridge);
			return c;
		});
	}

	public void showAuditLogViewer() {
		if (!appState.isAdmin()) {
			showError("Only admin users can view audit logs.");
			return;
		}
		AuditLogDao auditLogDao = new AuditLogDao(dbSessionProvider);
		Parent viewerRoot = load("/fxml/audit-log-viewer.fxml", controller ->
		{
			AuditLogViewerController c = (AuditLogViewerController) controller;
			c.init(appState, auditLogDao, new com.shale.data.dao.EntityActionAuditDao(), new UserDao(dbSessionProvider), dbSessionProvider, runtimeBridge);
			return c;
		});
		Stage dialogStage = AppDialogs.createModalStage(stage, "Audit Log");
		Region body = viewerRoot instanceof Region region ? region : new VBox(viewerRoot);
		body.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
		VBox shell = AppDialogs.createSecondaryWindowShell(dialogStage, "Audit Log", dialogStage::close, body);
		Scene scene = new Scene(shell, 1400, 720);
		scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/css/app.css")).toExternalForm());
		dialogStage.setScene(scene);
		dialogStage.show();
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
					new UserDao(dbSessionProvider),
					new TaskDao(dbSessionProvider),
					new CalendarEventDao(dbSessionProvider));
			CaseDetailService caseDetailService = new CaseDetailService(caseDao, appState);
			c.init(appState, searchService, caseDetailService, runtimeBridge, query, onOpenCase, onOpenContact, onOpenOrganization, onOpenUser, this::openTaskProfile, this::openCalendarEventFromNotification);
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
			CalendarService calendarService = new CalendarService(new CalendarEventTypeDao(dbSessionProvider), new CalendarEventDao(dbSessionProvider), new CalendarFeedDao(dbSessionProvider), notificationDao, runtimeBridge);
			CaseTaskService caseTaskService = new CaseTaskService(taskDao, userDao, runtimeBridge, notificationDao);
			UserDetailService userDetailService = new UserDetailService(userDao, caseDao, taskDao);
				c.init(userId, userDetailService, appState, runtimeBridge, relatedCaseId ->
				{
					System.out.println("[Navigation] Rewired user related-case callback via SceneManager.openCaseProfile");
					openCaseProfile(relatedCaseId, "OVERVIEW");
				}, this::openUserProfile, caseTaskService, phiReadAuditService, calendarService);
				return c;
			});
	}

	public Parent createContactView(int contactId, Consumer<Integer> onOpenCase, Runnable onContactDeleted) {
		return load("/fxml/contact.fxml", controller ->
		{
			ContactViewController c = (ContactViewController) controller;
			ContactDao contactDao = new ContactDao(dbSessionProvider);
			ContactDetailService contactDetailService = new ContactDetailService(contactDao);
				c.init(contactId, contactDetailService, appState, onOpenCase, new CaseServiceAdapter(new CaseDao(dbSessionProvider)), onContactDeleted, phiReadAuditService, this::openContactProfile, runtimeBridge);
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
			UserBoardLanePreferencesDao userBoardLanePreferencesDao = new UserBoardLanePreferencesDao(dbSessionProvider);
			UserPreferencesService userPreferencesService = new UserPreferencesService(new UserPreferencesDao(dbSessionProvider), appState);
			NotificationDao notificationDao = new NotificationDao(dbSessionProvider);
			CalendarService calendarService = new CalendarService(new CalendarEventTypeDao(dbSessionProvider), new CalendarEventDao(dbSessionProvider), new CalendarFeedDao(dbSessionProvider), notificationDao, runtimeBridge);
			CaseTaskService caseTaskService = new CaseTaskService(taskDao, userDao, runtimeBridge, notificationDao);
			c.init(appState, runtimeBridge, caseDao, caseTaskService, userBoardLanePreferencesDao, userPreferencesService, notificationCenterService, this::openNotificationCenterFromDashboard, onOpenCase, onOpenUser, phiReadAuditService);
			return c;
		});
	}

	private void openNotificationCenterFromDashboard() {
		MainController mainController = resolveMainController();
		if (mainController != null) {
			mainController.openNotificationCenter();
		}
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
			CalendarFeedDao calendarFeedDao = new CalendarFeedDao(dbSessionProvider);
			CalendarService calendarService = new CalendarService(new CalendarEventTypeDao(dbSessionProvider), new CalendarEventDao(dbSessionProvider), calendarFeedDao, notificationDao, runtimeBridge);
			c.init(caseId, caseDao, caseDetailService, caseTaskService, calendarService, calendarFeedDao, new CaseServiceAdapter(caseDao), organizationDao, contactDao, appState, runtimeBridge, onCaseDeleted, phiReadAuditService);
			c.setMaterialRequestService(new MaterialRequestServiceAdapter(new MaterialRequestDao(dbSessionProvider)));
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
			WindowSizingUtil.sizeModalStage(dialog, stage, 760, 720);
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
			controller.init(appState, caseDao, organizationDao, runtimeBridge, dialog, onCaseCreated);

			VBox dialogRoot = new VBox(
					AppDialogs.createSecondaryWindowHeader(dialog, "New Intake", dialog::close),
					root);
			dialogRoot.getStyleClass().add("secondary-window-shell");
			VBox.setVgrow(root, Priority.ALWAYS);

			Scene dialogScene = new Scene(dialogRoot);
			dialogScene.getStylesheets().add(Objects.requireNonNull(
					getClass().getResource("/css/app.css")).toExternalForm());
			dialog.setScene(dialogScene);
			WindowSizingUtil.sizeModalStage(dialog, stage, 1180, 760);
			dialog.showAndWait();
		} catch (IOException e) {
			throw new RuntimeException("Failed to open New Intake dialog", e);
		}
	}

	private void openStatusProfile(Integer statusId) {
		System.out.println("Navigate to Status: " + statusId);
	}


	private void openCalendarTaskLocation(Long taskId) {
		if (taskId == null || taskId <= 0) {
			System.err.println("Ignoring calendar task navigation for invalid taskId: " + taskId);
			return;
		}
		Integer shaleClientId = appState.getShaleClientId();
		if (shaleClientId == null || shaleClientId <= 0) {
			AppDialogs.showError(stage, "Tasks", "You must be signed in to view task details.");
			return;
		}
		CaseTaskService caseTaskService = new CaseTaskService(
				new TaskDao(dbSessionProvider),
				new UserDao(dbSessionProvider),
				runtimeBridge,
				new NotificationDao(dbSessionProvider));
		new Thread(() -> {
			try {
				TaskDetailDto detail = caseTaskService.loadTaskDetail(taskId, shaleClientId);
				if (detail == null) {
					Platform.runLater(() -> AppDialogs.showError(stage, "Tasks", "Task was not found or may have been deleted."));
					return;
				}
				long caseId = detail.caseId();
				Platform.runLater(() -> {
					if (caseId > 0 && caseId <= Integer.MAX_VALUE) {
						openCaseProfile((int) caseId, "TASKS");
					} else {
						openMyShaleView();
					}
				});
			} catch (Exception ex) {
				Platform.runLater(() -> AppDialogs.showError(stage, "Tasks", "Failed to load task details. " + rootCauseMessage(ex)));
			}
		}, "calendar-task-navigation-" + taskId).start();
	}

	public void openTaskProfile(Long taskId) {
		openTaskProfile(taskId, null);
	}

	public void openTaskProfile(Long taskId, Runnable onTaskChanged) {
		if (taskId == null || taskId <= 0) {
			System.err.println("Ignoring task navigation for invalid taskId: " + taskId);
			return;
		}
		if (!taskDetailDialogInFlight.compareAndSet(false, true)) {
			return;
		}
		Integer shaleClientId = appState.getShaleClientId();
		Integer currentUserId = appState.getUserId();
		if (shaleClientId == null || shaleClientId <= 0 || currentUserId == null || currentUserId <= 0) {
			taskDetailDialogInFlight.set(false);
			AppDialogs.showError(stage, "Tasks", "You must be signed in to view task details.");
			return;
		}
		CaseTaskService caseTaskService = new CaseTaskService(
				new TaskDao(dbSessionProvider),
				new UserDao(dbSessionProvider),
				runtimeBridge,
				new NotificationDao(dbSessionProvider));
		new Thread(() -> {
			try {
				TaskDetailDto initialDetail = caseTaskService.loadTaskDetail(taskId, shaleClientId);
				if (initialDetail == null) {
					taskDetailDialogInFlight.set(false);
					Platform.runLater(() -> AppDialogs.showError(stage, "Tasks", "Task was not found or may have been deleted."));
					return;
				}
				Platform.runLater(() -> showTaskDetailDialog(taskId, shaleClientId, currentUserId, caseTaskService, onTaskChanged, initialDetail));
			} catch (Exception ex) {
				taskDetailDialogInFlight.set(false);
				Platform.runLater(() -> AppDialogs.showError(stage, "Tasks", "Failed to load task details. " + rootCauseMessage(ex)));
			}
		}, "scene-manager-open-task-detail-" + taskId).start();
	}

	private void showTaskDetailDialog(
			long taskId,
			int shaleClientId,
			int currentUserId,
			CaseTaskService caseTaskService,
			Runnable onTaskChanged,
			TaskDetailDto initialDetail) {
		TaskDetailDialog.TaskDetailModel model = new TaskDetailDialog.TaskDetailModel(
				taskId,
				initialDetail == null ? 0L : initialDetail.caseId(),
				initialDetail == null ? "" : initialDetail.caseName(),
				initialDetail == null ? "" : initialDetail.caseResponsibleAttorney(),
				initialDetail == null ? "" : initialDetail.caseResponsibleAttorneyColor(),
				initialDetail == null ? null : initialDetail.caseNonEngagementLetterSent(),
				initialDetail == null ? "" : initialDetail.casePrimaryStatusName(),
				initialDetail == null ? "" : initialDetail.casePrimaryStatusColor(),
				initialDetail == null ? "" : initialDetail.casePracticeAreaColor(),
				initialDetail == null ? "" : initialDetail.title(),
				initialDetail == null ? "" : initialDetail.description(),
				initialDetail == null ? null : initialDetail.dueAt(),
				initialDetail == null ? null : initialDetail.statusId(),
				initialDetail == null ? null : initialDetail.priorityId(),
				initialDetail == null ? "" : initialDetail.createdByDisplayName(),
				List.of(),
				List.of(),
				List.of(),
				initialDetail != null && initialDetail.completedAt() != null);
		java.util.concurrent.atomic.AtomicBoolean dialogMutatedAssignments = new java.util.concurrent.atomic.AtomicBoolean(false);
		Window owner = stage.getScene() == null ? stage : stage.getScene().getWindow();
		phiReadAuditService.auditRead("Task.Detail.Read", "Task.Detail", "Task", taskId);
		phiReadAuditService.auditRead("Task.Activity.Read", "Task.Activity", "Task", taskId);
		var result = TaskDetailDialog.showAndWait(
				"SCENE_MANAGER",
				0L,
				owner,
				model,
				List.of(),
				List.of(),
				id -> {
					TaskDetailDto detail = caseTaskService.loadTaskDetail(id, shaleClientId);
					if (detail == null) throw new IllegalStateException("Task was not found or may have been deleted.");
					List<TaskStatusOptionDto> statuses = caseTaskService.loadActiveTaskStatuses(shaleClientId);
					List<TaskPriorityOptionDto> priorities = caseTaskService.loadActivePriorities(shaleClientId);
					return new TaskDetailDialog.CoreTaskHydration(detail, statuses, priorities);
				},
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
						dialogMutatedAssignments.set(true);
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
						dialogMutatedAssignments.set(true);
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
			taskDetailDialogInFlight.set(false);
			if (dialogMutatedAssignments.get()) runTaskChangedCallback(onTaskChanged);
			return;
		}
		taskDetailDialogInFlight.set(false);
		TaskDetailDialog.TaskDetailResult action = result.get();
		if (action.action() == TaskDetailDialog.TaskDetailAction.DELETE) {
			new Thread(() -> {
				try {
					caseTaskService.deleteTask(taskId, shaleClientId, currentUserId);
					runTaskChangedCallback(onTaskChanged);
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
				runTaskChangedCallback(onTaskChanged);
			} catch (Exception ex) {
				Platform.runLater(() -> AppDialogs.showError(stage, "Tasks", "Failed to save task. " + rootCauseMessage(ex)));
			}
		}, "scene-manager-save-task-" + taskId).start();
	}


	private static void runTaskChangedCallback(Runnable onTaskChanged) {
		if (onTaskChanged == null) return;
		Platform.runLater(onTaskChanged);
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
			scene = new Scene(root);
			scene.getStylesheets().add(Objects.requireNonNull(
					getClass().getResource("/css/app.css")).toExternalForm());
			stage.setScene(scene);
		} else {
			scene.setRoot(root);
		}
		stage.setTitle(title);
		if (!stage.isShowing()) {
			WindowSizingUtil.sizeMainStage(stage);
		}
		stage.show();
	}

	public void showError(String message) {
		System.out.println("*******************SceneManager.showError() " + message);
	}

	/** Deterministically releases all SceneManager-owned background work. */
	public void shutdown() {
		notificationPollingService.close();
		taskDueDateNotificationGenerator.stop();
		updatePollingService.stop();
		liveUpdateNotificationBridge.stop();
		connectivityNotificationProducer.stop();
		notificationBadgeCountExecutor.shutdownNow();
		notificationStartupExecutor.shutdownNow();
	}
}
