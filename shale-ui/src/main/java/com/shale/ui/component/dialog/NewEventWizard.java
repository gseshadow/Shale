package com.shale.ui.component.dialog;

import com.shale.core.dto.EffectiveCaseDateTypeDto;
import com.shale.core.model.CalendarEventType;
import com.shale.ui.component.factory.CaseCardFactory;
import com.shale.ui.util.ActionButtonFactory;
import com.shale.ui.util.ControlStyles;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * One non-persisting Calendar creation workflow for the two authoritative event domains.
 */
public final class NewEventWizard {
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("H:mm");

	private NewEventWizard() {
	}

	public enum SourceKind {
		GENERAL_EVENT, CASE_EVENT
	}

	public record TypeChoice(
			SourceKind sourceKind, int authoritativeTypeId, String name, String color,
			boolean supportsTime, int sortOrder
	) {
		public String groupLabel() {
			return sourceKind == SourceKind.GENERAL_EVENT ? "General Event" : "Case Event";
		}

		@Override
		public String toString() {
			return name;
		}
	}

	public record CaseDateInput(
			int caseDateTypeId, long caseId, LocalDateTime startsAt,
			LocalDateTime endsAt, boolean allDay, String notes
	) {
	}

	public record SaveRequest(
			SourceKind sourceKind, NewCalendarEventDialog.CreateCalendarEventInput general,
			CaseDateInput caseDate
	) {
	}

	public static List<TypeChoice> choices(List<CalendarEventType> general, List<EffectiveCaseDateTypeDto> cases) {
		List<TypeChoice> out = new ArrayList<>();
		if (general != null)
			general.stream().filter(CalendarEventType::active)
					.forEach(t -> out.add(new TypeChoice(SourceKind.GENERAL_EVENT, t.calendarEventTypeId(), t.name(), t.colorHex(), true, t.sortOrder())));
		if (cases != null)
			cases.stream().filter(t -> t.active() && !t.deleted())
					.forEach(t -> out.add(new TypeChoice(SourceKind.CASE_EVENT, t.id(), t.name(), t.color(), t.supportsTime(), t.sortOrder())));
		Comparator<TypeChoice> choiceOrder = Comparator
				.comparing((TypeChoice choice) -> choice.sourceKind())
				.thenComparingInt(TypeChoice::sortOrder)
				.thenComparing(choice -> java.util.Objects.toString(choice.name(), "").toLowerCase(Locale.ROOT))
				.thenComparingInt(TypeChoice::authoritativeTypeId);
		out.sort(choiceOrder);
		return List.copyOf(out);
	}

	public static Handle show(Window owner, int tenantId, LocalDate defaultDate,
			Supplier<List<NewCalendarEventDialog.CaseOption>> caseLoader,
			Supplier<List<NewCalendarEventDialog.AssignedUserOption>> userLoader,
			Function<SaveRequest, ? extends CompletionStage<String>> saver,
			Executor executor) {
		return new Handle(owner, tenantId, defaultDate, caseLoader, userLoader, saver, executor == null ? Runnable::run : executor);
	}

	public static final class Handle {
		private final Stage stage;
		private final int tenantId;
		private final Executor executor;
		private final Supplier<List<NewCalendarEventDialog.CaseOption>> caseLoader;
		private final Supplier<List<NewCalendarEventDialog.AssignedUserOption>> userLoader;
		private final Function<SaveRequest, ? extends CompletionStage<String>> saver;
		private final VBox content = new VBox(12);
		private final Label heading = new Label();
		private final Label error = new Label();
		private final Button back;
		private final Button next;
		private final Button save;
		private final AtomicBoolean submitting = new AtomicBoolean();
		private int generation;
		private Step step = Step.TYPE;
		private TypeChoice selectedType;
		private NewCalendarEventDialog.CaseOption selectedCase;
		private final TextField typeSearch = new TextField();
		private final ListView<TypeChoice> typeList = new ListView<>();
		private final TextField caseSearch = new TextField();
		private final ListView<NewCalendarEventDialog.CaseOption> caseList = new ListView<>();
		private final List<TypeChoice> loadedTypes = new ArrayList<>();
		private final List<NewCalendarEventDialog.CaseOption> loadedCases = new ArrayList<>();
		private final TextField title = new TextField();
		private final DatePicker date;
		private final TextField startTime = new TextField("9:00");
		private final ComboBox<Integer> duration = new ComboBox<>();
		private final CheckBox allDay = new CheckBox("All day");
		private final TextArea description = new TextArea();
		private final ComboBox<NewCalendarEventDialog.CaseOption> optionalCase = new ComboBox<>();
		private final ComboBox<NewCalendarEventDialog.AssignedUserOption> assignedUser = new ComboBox<>();
		private final DatePicker caseStartDate;
		private final DatePicker caseEndDate = new DatePicker();
		private final TextField caseStartTime = new TextField("9:00");
		private final TextField caseEndTime = new TextField();
		private final CheckBox caseAllDay = new CheckBox("All day");
		private final TextArea notes = new TextArea();

		private Handle(Window owner, int tenantId, LocalDate initialDate,
				Supplier<List<NewCalendarEventDialog.CaseOption>> caseLoader,
				Supplier<List<NewCalendarEventDialog.AssignedUserOption>> userLoader,
				Function<SaveRequest, ? extends CompletionStage<String>> saver, Executor executor) {
			this.tenantId = tenantId;
			this.caseLoader = caseLoader;
			this.userLoader = userLoader;
			this.saver = saver;
			this.executor = executor;
			date = new DatePicker(initialDate == null ? LocalDate.now() : initialDate);
			caseStartDate = new DatePicker(date.getValue());
			stage = AppDialogs.createModalStage(owner, "New Event");
			heading.getStyleClass().add("app-dialog-title");
			error.getStyleClass().add("form-validation-message");
			error.setWrapText(true);
			hide(error);
			back = ActionButtonFactory.semantic("Back", e -> back(), ControlStyles.Purpose.SECONDARY, ControlStyles.Size.STANDARD);
			Button cancel = ActionButtonFactory.semantic("Cancel", e ->
			{
				if (!submitting.get())
					close();
			}, ControlStyles.Purpose.SECONDARY, ControlStyles.Size.STANDARD);
			cancel.setCancelButton(true);
			cancel.setAccessibleText("Cancel new event");
			next = ActionButtonFactory.semantic("Next", e -> next(), ControlStyles.Purpose.PRIMARY, ControlStyles.Size.STANDARD);
			save = ActionButtonFactory.semantic("Save", e -> submit(), ControlStyles.Purpose.PRIMARY, ControlStyles.Size.STANDARD);
			Region spacer = new Region();
			HBox.setHgrow(spacer, Priority.ALWAYS);
			HBox actions = new HBox(8, back, spacer, cancel, next, save);
			actions.setAlignment(Pos.CENTER_RIGHT);
			VBox body = new VBox(12, heading, content, error, actions);
			body.setPadding(new Insets(20, 24, 16, 24));
			Scene scene = new Scene(AppDialogs.createSecondaryWindowShell(stage, "New Event", () ->
			{
				if (!submitting.get())
					close();
			}, body), 760, 620);
			scene.getStylesheets().add(Objects.requireNonNull(NewEventWizard.class.getResource("/css/app.css")).toExternalForm());
			stage.setScene(scene);
			scene.setOnKeyPressed(e ->
			{
				if (e.getCode() == KeyCode.ESCAPE && !submitting.get()) {
					close();
					e.consume();
				}
			});
			configureControls();
			renderType();
			stage.show();
		}

		public boolean isShowing() {
			return stage.isShowing();
		}

		public int tenantId() {
			return tenantId;
		}

		public void populateTypes(int resultTenantId, List<CalendarEventType> general, List<EffectiveCaseDateTypeDto> cases, int requestGeneration) {
			if (!accept(resultTenantId, requestGeneration, Step.TYPE))
				return;
			loadedTypes.clear();
			loadedTypes.addAll(choices(general, cases));
			refreshTypeFilter();
			typeList.setPlaceholder(new Label(loadedTypes.isEmpty() ? "No active event types are available." : "No event types match this search."));
		}

		public void showTypeLoadError(int resultTenantId, int requestGeneration, String message) {
			if (accept(resultTenantId, requestGeneration, Step.TYPE)) {
				showError(message);
				typeList.setPlaceholder(new Label("Unable to load event types. Close and retry."));
			}
		}

		public int beginTypeLoad() {
			return ++generation;
		}

		public void close() {
			generation++;
			stage.close();
		}

		private boolean accept(int resultTenantId, int requestGeneration, Step expected) {
			return stage.isShowing() && tenantId == resultTenantId && generation == requestGeneration && step == expected;
		}

		private void configureControls() {
			typeSearch.setPromptText("Search event types");
			typeSearch.setAccessibleText("Search event types");
			ControlStyles.formControl(typeSearch);
			typeList.setAccessibleText("Event types grouped as General Event and Case Event");
			typeList.setCellFactory(v -> new ListCell<>() {
				@Override
				protected void updateItem(TypeChoice x, boolean empty) {
					super.updateItem(x, empty);
					setGraphic(empty || x == null ? null : new VBox(2, new Label(x.name()), muted(x.groupLabel())));
					setText(null);
				}
			});
			typeSearch.textProperty().addListener((o, a, b) -> refreshTypeFilter());
			typeList.getSelectionModel().selectedItemProperty().addListener((o, a, b) ->
			{
				if (a != null && b != null && a.sourceKind() != b.sourceKind()) {
					selectedCase = null;
					title.clear();
					description.clear();
					notes.clear();
					optionalCase.setValue(null);
					assignedUser.setValue(null);
				}
				selectedType = b;
				updateActions();
			});
			caseSearch.setPromptText("Search cases");
			caseSearch.setAccessibleText("Search cases");
			ControlStyles.formControl(caseSearch);
			CaseCardFactory cards = new CaseCardFactory(id ->
			{
			});
			caseList.setAccessibleText("Cases");
			caseList.setCellFactory(v -> new ListCell<>() {
				@Override
				protected void updateItem(NewCalendarEventDialog.CaseOption x, boolean empty) {
					super.updateItem(x, empty);
					setText(null);
					setGraphic(empty || x == null ? null
							: cards.create(new CaseCardFactory.CaseCardModel(x.caseId(), x.displayName(), null, null, x.responsibleAttorney(), x.responsibleAttorneyColor(), x
									.nonEngagementLetterSent()), CaseCardFactory.Variant.MINI));
				}
			});
			caseSearch.textProperty().addListener((o, a, b) -> refreshCaseFilter());
			caseList.getSelectionModel().selectedItemProperty().addListener((o, a, b) ->
			{
				selectedCase = b;
				updateActions();
			});
			duration.getItems().setAll(15, 30, 45, 60, 90, 120, 180, 240);
			duration.setValue(60);
			description.setPrefRowCount(4);
			notes.setPrefRowCount(4);
			List<Control> forms = List.of(title, date, startTime, duration, description, optionalCase, assignedUser, caseStartDate, caseEndDate, caseStartTime, caseEndTime, notes);
			forms.forEach(ControlStyles::formControl);
			allDay.selectedProperty().addListener((o, a, b) ->
			{
				startTime.setDisable(b);
				duration.setDisable(b);
			});
			caseAllDay.setSelected(true);
			caseStartTime.setDisable(true);
			caseEndTime.setDisable(true);
			caseAllDay.selectedProperty().addListener((o, a, b) ->
			{
				boolean disable = b || selectedType != null && !selectedType.supportsTime();
				caseStartTime.setDisable(disable);
				caseEndTime.setDisable(disable);
			});
			CompletableFuture.supplyAsync(() -> userLoader == null ? List.<NewCalendarEventDialog.AssignedUserOption>of() : userLoader.get(), executor).thenAccept(rows -> Platform
					.runLater(() ->
					{
						if (stage.isShowing())
							assignedUser.getItems().setAll(rows == null ? List.of() : rows);
					}));
		}

		private void renderType() {
			step = Step.TYPE;
			heading.setText("Choose event type");
			content.getChildren().setAll(typeSearch, typeList);
			typeList.setPrefHeight(430);
			hide(error);
			updateActions();
			Platform.runLater(typeSearch::requestFocus);
		}

		private void renderCase() {
			step = Step.CASE;
			heading.setText("Choose a Case");
			content.getChildren().setAll(summary(selectedType), caseSearch, caseList);
			caseList.setPrefHeight(390);
			hide(error);
			updateActions();
			Platform.runLater(caseSearch::requestFocus);
			loadCases();
		}

		private void renderDetails() {
			step = Step.DETAILS;
			heading.setText("Event details");
			VBox summary = new VBox(6, summary(selectedType));
			if (selectedType.sourceKind() == SourceKind.CASE_EVENT)
				summary.getChildren().add(summary(selectedCase));
			GridPane grid = new GridPane();
			grid.setHgap(10);
			grid.setVgap(9);
			int r = 0;
			if (selectedType.sourceKind() == SourceKind.GENERAL_EVENT) {
				add(grid, r++, "Title", title);
				add(grid, r++, "Date", date);
				add(grid, r++, "Start time", startTime);
				add(grid, r++, "Duration", duration);
				add(grid, r++, "", allDay);
				add(grid, r++, "Description", description);
				add(grid, r++, "Case (optional)", optionalCase);
				add(grid, r, "Assigned to", assignedUser);
				loadGeneralCases();
			} else {
				add(grid, r++, "Start date", caseStartDate);
				add(grid, r++, "Start time", caseStartTime);
				add(grid, r++, "End date", caseEndDate);
				add(grid, r++, "End time", caseEndTime);
				add(grid, r++, "", caseAllDay);
				add(grid, r, "Notes", notes);
				caseAllDay.setDisable(!selectedType.supportsTime());
				if (!selectedType.supportsTime())
					caseAllDay.setSelected(true);
			}
			content.getChildren().setAll(summary, grid);
			hide(error);
			updateActions();
			Platform.runLater(() -> (selectedType.sourceKind() == SourceKind.GENERAL_EVENT ? title : caseStartDate).requestFocus());
		}

		private void next() {
			if (submitting.get())
				return;
			if (step == Step.TYPE) {
				if (selectedType == null) {
					showError("Choose an event type.");
					return;
				}
				if (selectedType.sourceKind() == SourceKind.CASE_EVENT)
					renderCase();
				else
					renderDetails();
			} else if (step == Step.CASE) {
				if (selectedCase == null) {
					showError("Choose a Case.");
					return;
				}
				renderDetails();
			}
		}

		private void back() {
			if (submitting.get())
				return;
			if (step == Step.DETAILS) {
				if (selectedType.sourceKind() == SourceKind.CASE_EVENT)
					renderCase();
				else
					renderType();
			} else if (step == Step.CASE) {
				selectedCase = null;
				loadedCases.clear();
				renderType();
			}
		}

		private void loadCases() {
			int request = ++generation;
			caseList.setPlaceholder(new Label("Loading cases…"));
			CompletableFuture.supplyAsync(() -> caseLoader == null ? List.<NewCalendarEventDialog.CaseOption>of() : caseLoader.get(), executor).whenComplete((rows,
					failure) -> Platform.runLater(() ->
					{
						if (!accept(tenantId, request, Step.CASE))
							return;
						if (failure != null) {
							caseList.setPlaceholder(new Label("Unable to load cases. Go Back and retry."));
							return;
						}
						loadedCases.clear();
						loadedCases.addAll(rows == null ? List.of() : rows);
						refreshCaseFilter();
						optionalCase.getItems().setAll(loadedCases);
						caseList.setPlaceholder(new Label(loadedCases.isEmpty() ? "No active cases are available." : "No cases match this search."));
					}));
		}

		private void loadGeneralCases() {
			CompletableFuture.supplyAsync(() -> caseLoader == null ? List.<NewCalendarEventDialog.CaseOption>of() : caseLoader.get(), executor).thenAccept(rows -> Platform
					.runLater(() ->
					{
						if (stage.isShowing() && step == Step.DETAILS && selectedType != null && selectedType.sourceKind() == SourceKind.GENERAL_EVENT)
							optionalCase.getItems().setAll(rows == null ? List.of() : rows);
					}));
		}

		private void submit() {
			if (submitting.get())
				return;
			Optional<SaveRequest> request = read();
			if (request.isEmpty())
				return;
			if (!submitting.compareAndSet(false, true))
				return;
			setBusy(true);
			CompletionStage<String> result;
			try {
				result = saver == null ? CompletableFuture.completedFuture("Save is unavailable.") : saver.apply(request.get());
			} catch (RuntimeException ex) {
				result = CompletableFuture.completedFuture("Unable to save this event.");
			}
			if (result == null)
				result = CompletableFuture.completedFuture("Save is unavailable.");
			result.whenComplete((message, failure) -> Platform.runLater(() ->
			{
				if (!stage.isShowing())
					return;
				submitting.set(false);
				setBusy(false);
				String shown = failure == null ? message : "Unable to save this event.";
				if (shown == null || shown.isBlank())
					close();
				else
					showError(shown);
			}));
		}

		private Optional<SaveRequest> read() {
			if (selectedType == null)
				return Optional.empty();
			if (selectedType.sourceKind() == SourceKind.GENERAL_EVENT) {
				if (title.getText() == null || title.getText().isBlank()) {
					showError("Title is required.");
					return Optional.empty();
				}
				if (date.getValue() == null) {
					showError("Date is required.");
					return Optional.empty();
				}
				LocalTime time = null;
				if (!allDay.isSelected())
					try {
						time = LocalTime.parse(startTime.getText().trim(), TIME);
					} catch (Exception ex) {
						showError("Start time must be a valid time such as 9:30.");
						return Optional.empty();
					}
				return Optional.of(new SaveRequest(SourceKind.GENERAL_EVENT, new NewCalendarEventDialog.CreateCalendarEventInput(title.getText().trim(), selectedType
						.authoritativeTypeId(), date.getValue(), allDay.isSelected(), time, duration.getValue() == null ? 60 : duration.getValue(), description.getText(),
						optionalCase.getValue() == null ? null : optionalCase.getValue().caseId(), assignedUser.getValue() == null ? null : assignedUser.getValue().userId()),
						null));
			}
			if (selectedCase == null) {
				showError("A Case is required for a Case Event.");
				return Optional.empty();
			}
			if (caseStartDate.getValue() == null) {
				showError("Start date is required.");
				return Optional.empty();
			}
			boolean ad = caseAllDay.isSelected();
			LocalDateTime start, end = null;
			try {
				start = ad ? caseStartDate.getValue().atStartOfDay() : LocalDateTime.of(caseStartDate.getValue(), parse(caseStartTime.getText()));
				if (caseEndDate.getValue() != null || !blank(caseEndTime.getText())) {
					if (caseEndDate.getValue() == null)
						throw new IllegalArgumentException("End date is required when an end time is entered.");
					end = ad ? caseEndDate.getValue().atStartOfDay() : LocalDateTime.of(caseEndDate.getValue(), parse(caseEndTime.getText()));
				}
			} catch (IllegalArgumentException ex) {
				showError(ex.getMessage() == null ? "Enter valid date and time values." : ex.getMessage());
				return Optional.empty();
			}
			if (end != null && end.isBefore(start)) {
				showError("End must not be before start.");
				return Optional.empty();
			}
			return Optional.of(new SaveRequest(SourceKind.CASE_EVENT, null, new CaseDateInput(selectedType.authoritativeTypeId(), selectedCase.caseId(), start, end, ad, notes
					.getText())));
		}

		private void setBusy(boolean busy) {
			back.setDisable(busy);
			next.setDisable(busy);
			save.setDisable(busy);
			typeSearch.setDisable(busy);
			caseSearch.setDisable(busy);
		}

		private void updateActions() {
			back.setVisible(step != Step.TYPE);
			back.setManaged(step != Step.TYPE);
			next.setVisible(step != Step.DETAILS);
			next.setManaged(step != Step.DETAILS);
			save.setVisible(step == Step.DETAILS);
			save.setManaged(step == Step.DETAILS);
			next.setDisable(step == Step.TYPE ? selectedType == null : selectedCase == null);
			next.setDefaultButton(step != Step.DETAILS && !next.isDisable());
			save.setDefaultButton(step == Step.DETAILS);
		}

		private void refreshTypeFilter() {
			String q = safe(typeSearch.getText()).trim().toLowerCase(Locale.ROOT);
			typeList.setItems(FXCollections.observableArrayList(loadedTypes.stream().filter(t -> q.isEmpty() || safe(t.name()).toLowerCase(Locale.ROOT).contains(q) || t
					.groupLabel().toLowerCase(Locale.ROOT).contains(q)).toList()));
		}

		private void refreshCaseFilter() {
			String q = safe(caseSearch.getText()).trim().toLowerCase(Locale.ROOT);
			caseList.setItems(FXCollections.observableArrayList(loadedCases.stream().filter(c -> q.isEmpty() || safe(c.displayName()).toLowerCase(Locale.ROOT).contains(q) || safe(c
					.responsibleAttorney()).toLowerCase(Locale.ROOT).contains(q)).toList()));
		}

		private static Node summary(Object value) {
			Label l = new Label(value instanceof TypeChoice t ? t.groupLabel() + ": " + t.name()
					: value instanceof NewCalendarEventDialog.CaseOption c ? "Case: " + c.displayName() : "");
			l.getStyleClass().add("app-dialog-message");
			l.setAccessibleRole(javafx.scene.AccessibleRole.TEXT);
			return l;
		}

		private static Label muted(String text) {
			Label l = new Label(text);
			l.getStyleClass().add("app-dialog-message");
			return l;
		}

		private static void add(GridPane g, int row, String label, Node n) {
			g.add(new Label(label), 0, row);
			g.add(n, 1, row);
			GridPane.setHgrow(n, Priority.ALWAYS);
		}

		private static LocalTime parse(String value) {
			if (blank(value))
				throw new IllegalArgumentException("Time is required for a timed Case Event.");
			try {
				return LocalTime.parse(value.trim(), TIME);
			} catch (DateTimeParseException ex) {
				throw new IllegalArgumentException("Time must be valid, such as 9:30.");
			}
		}

		private void showError(String message) {
			error.setText(message);
			error.setVisible(true);
			error.setManaged(true);
		}

		private static void hide(Node n) {
			n.setVisible(false);
			n.setManaged(false);
		}

		private static boolean blank(String s) {
			return s == null || s.isBlank();
		}

		private static String safe(String s) {
			return s == null ? "" : s;
		}

		private enum Step {
			TYPE, CASE, DETAILS
		}
	}
}
