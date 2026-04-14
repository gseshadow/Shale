package com.shale.ui.controller.support;

import com.shale.data.dao.CaseDao;
import com.shale.data.dao.OrganizationDao;
import com.shale.ui.component.dialog.AppDialogs;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class PartyAddWorkflowDialog {

	public record AddPartyDraft(
			String entityType,
			Long entityId,
			String entityLabel,
			long partyRoleId,
			String side,
			boolean primary,
			String notes,
			boolean createNew,
			String contactFirstName,
			String contactLastName,
			String organizationName,
			Integer organizationTypeId) {
	}

	public record PartySideOption(String label, String value) {
	}

	private record PartyRoleOption(long id, String label) {
	}

	private record PartyEntityOption(String entityType, Long id, String label) {
	}

	private PartyAddWorkflowDialog() {
	}

	public static AddPartyDraft show(Window owner,
			List<CaseDao.PartyRoleRow> partyRoles,
			List<CaseDao.SelectableContactRow> contacts,
			List<CaseDao.SelectableOrganizationRow> organizations,
			List<OrganizationDao.OrganizationTypeRow> organizationTypes,
			List<PartySideOption> sideOptions) {
		Long defaultPartyRoleId = partyRoles.stream()
				.filter(r -> "party".equalsIgnoreCase(safeText(r.name())))
				.map(CaseDao.PartyRoleRow::id)
				.findFirst()
				.orElse(partyRoles.isEmpty() ? null : partyRoles.get(0).id());

		class WizardState {
			int step = 1;
			String mode = null;
			String entityType = null;
			PartyEntityOption selectedEntity = null;
			String createContactFirstName = null;
			String createContactLastName = null;
			String createOrganizationName = null;
			Integer createOrganizationTypeId = organizationTypes.isEmpty() ? null : organizationTypes.get(0).organizationTypeId();
			Long partyRoleId = defaultPartyRoleId;
			String affiliation = null;
			boolean primary = false;
			String notes = null;
		}
		WizardState state = new WizardState();

		Dialog<AddPartyDraft> dialog = new Dialog<>();
		AppDialogs.applySecondaryDialogShell(dialog, "Add Party");
		dialog.setTitle("Add Party");
		if (owner != null) {
			dialog.initOwner(owner);
		}
		ButtonType backType = new ButtonType("Back", ButtonData.LEFT);
		ButtonType addType = new ButtonType("Add Party", ButtonData.OK_DONE);
		dialog.getDialogPane().getButtonTypes().addAll(backType, addType, ButtonType.CANCEL);

		Node backButton = dialog.getDialogPane().lookupButton(backType);
		Node addButton = dialog.getDialogPane().lookupButton(addType);
		Node cancelButton = dialog.getDialogPane().lookupButton(ButtonType.CANCEL);

		Label titleLabel = new Label();
		titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: 700;");
		Label subtitleLabel = new Label();
		subtitleLabel.setWrapText(true);

		Button createNewButton = new Button("Create New");
		Button selectExistingButton = new Button("Select Existing");
		createNewButton.setMinWidth(200);
		selectExistingButton.setMinWidth(200);

		Button contactButton = new Button("Contact");
		Button organizationButton = new Button("Organization");
		contactButton.setMinWidth(200);
		organizationButton.setMinWidth(200);
		applyToolbarButtonClasses(createNewButton, "app-toolbar-button-primary");
		applyToolbarButtonClasses(selectExistingButton, "app-toolbar-button-primary");
		applyToolbarButtonClasses(contactButton, "app-toolbar-button-primary");
		applyToolbarButtonClasses(organizationButton, "app-toolbar-button-primary");
		applyToolbarButtonClasses(asButton(addButton), "app-toolbar-button-primary");
		applyToolbarButtonClasses(asButton(backButton), "app-toolbar-button-neutral");
		applyToolbarButtonClasses(asButton(cancelButton), "app-toolbar-button-neutral");

		TextField createFirstNameField = new TextField();
		TextField createLastNameField = new TextField();
		TextField createOrganizationNameField = new TextField();
		ChoiceBox<OrganizationDao.OrganizationTypeRow> createOrganizationTypeChoice = new ChoiceBox<>();
		createOrganizationTypeChoice.getItems().setAll(organizationTypes);
		createOrganizationTypeChoice.setConverter(new javafx.util.StringConverter<>() {
			@Override public String toString(OrganizationDao.OrganizationTypeRow object) { return object == null ? "" : safeText(object.name()); }
			@Override public OrganizationDao.OrganizationTypeRow fromString(String string) { return null; }
		});
		if (!organizationTypes.isEmpty()) {
			createOrganizationTypeChoice.setValue(organizationTypes.get(0));
		}

		ChoiceBox<PartyRoleOption> roleChoice = new ChoiceBox<>();
		partyRoles.stream().map(r -> new PartyRoleOption(r.id(), toPartyRoleLabel(r.name(), r.id()))).forEach(roleChoice.getItems()::add);
		roleChoice.setConverter(new javafx.util.StringConverter<>() {
			@Override public String toString(PartyRoleOption object) { return object == null ? "" : object.label; }
			@Override public PartyRoleOption fromString(String string) { return null; }
		});
		if (!roleChoice.getItems().isEmpty()) {
			PartyRoleOption defaultRole = roleChoice.getItems().stream()
					.filter(r -> r.id() == defaultPartyRoleId)
					.findFirst()
					.orElse(roleChoice.getItems().get(0));
			roleChoice.setValue(defaultRole);
		}

		ChoiceBox<PartySideOption> sideChoice = new ChoiceBox<>();
		sideChoice.getItems().addAll(sideOptions);
		sideChoice.setConverter(new javafx.util.StringConverter<>() {
			@Override public String toString(PartySideOption object) { return object == null ? "" : object.label; }
			@Override public PartySideOption fromString(String string) { return null; }
		});
		sideChoice.setValue(sideChoice.getItems().stream()
				.filter(s -> s != null && s.value == null)
				.findFirst()
				.orElse(sideChoice.getItems().isEmpty() ? null : sideChoice.getItems().get(0)));

		CheckBox primaryCheck = new CheckBox("Primary");
		TextArea notesArea = new TextArea();
		notesArea.setPrefRowCount(3);
		notesArea.setWrapText(true);

		TextField searchField = new TextField();
		searchField.setPromptText("Search by name");
		javafx.scene.control.ListView<PartyEntityOption> existingList = new javafx.scene.control.ListView<>();
		existingList.setPrefHeight(260);
		existingList.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
			@Override
			protected void updateItem(PartyEntityOption item, boolean empty) {
				super.updateItem(item, empty);
				setText(empty || item == null ? null : item.label());
			}
		});

		VBox contentBox = new VBox(10);
		contentBox.setAlignment(Pos.TOP_CENTER);
		contentBox.setPadding(new Insets(16));
		contentBox.getChildren().addAll(titleLabel, subtitleLabel);
		dialog.getDialogPane().setContent(contentBox);

		Runnable refreshExistingList = () -> {
			String query = safeText(searchField.getText()).toLowerCase(Locale.ROOT);
			List<PartyEntityOption> options = new java.util.ArrayList<>();
			if ("organization".equals(state.entityType)) {
				for (CaseDao.SelectableOrganizationRow org : organizations) {
					String name = safeText(org.name());
					if (name.isBlank()) name = "Organization #" + org.id();
					String type = safeText(org.organizationTypeName());
					String label = type.isBlank() ? name : name + " — " + type;
					options.add(new PartyEntityOption("organization", Long.valueOf(org.id()), label));
				}
			} else {
				for (CaseDao.SelectableContactRow contact : contacts) {
					String name = safeText(contact.displayName());
					if (name.isBlank()) name = "Contact #" + contact.id();
					String secondary = safeText(contact.email());
					if (secondary.isBlank()) secondary = safeText(contact.phone());
					String label = secondary.isBlank() ? name : name + " — " + secondary;
					options.add(new PartyEntityOption("contact", Long.valueOf(contact.id()), label));
				}
			}
			if (!query.isBlank()) {
				options = options.stream().filter(o -> safeText(o.label()).toLowerCase(Locale.ROOT).contains(query)).toList();
			}
			existingList.getItems().setAll(options);
			if (state.selectedEntity != null) {
				existingList.getItems().stream()
						.filter(o -> Objects.equals(o.entityType(), state.selectedEntity.entityType()) && Objects.equals(o.id(), state.selectedEntity.id()))
						.findFirst().ifPresent(existingList.getSelectionModel()::select);
			}
			if (existingList.getSelectionModel().getSelectedItem() == null && !options.isEmpty()) {
				existingList.getSelectionModel().selectFirst();
				state.selectedEntity = existingList.getSelectionModel().getSelectedItem();
			}
		};

		Runnable syncStateFromControls = () -> {
			PartyRoleOption role = roleChoice.getValue();
			PartySideOption side = sideChoice.getValue();
			state.partyRoleId = role == null ? null : role.id();
			state.affiliation = side == null ? null : side.value();
			state.primary = primaryCheck.isSelected();
			state.notes = notesArea.getText();
			state.createContactFirstName = createFirstNameField.getText();
			state.createContactLastName = createLastNameField.getText();
			state.createOrganizationName = createOrganizationNameField.getText();
			OrganizationDao.OrganizationTypeRow orgType = createOrganizationTypeChoice.getValue();
			state.createOrganizationTypeId = orgType == null ? null : orgType.organizationTypeId();
			state.selectedEntity = existingList.getSelectionModel().getSelectedItem();
		};

		Runnable refreshAddButtonState = () -> {
			syncStateFromControls.run();
			boolean enabled = false;
			if (state.step == 3 && state.partyRoleId != null && state.affiliation != null) {
				if ("create".equals(state.mode)) {
					if ("contact".equals(state.entityType)) {
						enabled = !safeText(state.createContactFirstName).isBlank() || !safeText(state.createContactLastName).isBlank();
					} else if ("organization".equals(state.entityType)) {
						enabled = !safeText(state.createOrganizationName).isBlank() && state.createOrganizationTypeId != null && state.createOrganizationTypeId > 0;
					}
				} else if ("select".equals(state.mode)) {
					enabled = state.selectedEntity != null;
				}
			}
			addButton.setDisable(!enabled);
			setVisibleManaged(addButton, state.step == 3);
			setVisibleManaged(backButton, state.step > 1);
		};

		Runnable renderStep = () -> {
			contentBox.getChildren().setAll(titleLabel, subtitleLabel);
			if (state.step == 1) {
				titleLabel.setText("Step 1");
				subtitleLabel.setText("Create new or select from existing");
				HBox choices = new HBox(14, createNewButton, selectExistingButton);
				choices.setAlignment(Pos.CENTER);
				contentBox.getChildren().add(choices);
				dialog.getDialogPane().setPrefSize(560, 260);
				dialog.getDialogPane().setMinSize(560, 260);
			} else if (state.step == 2) {
				titleLabel.setText("Step 2");
				subtitleLabel.setText("Contact or Organization");
				HBox choices = new HBox(14, contactButton, organizationButton);
				choices.setAlignment(Pos.CENTER);
				contentBox.getChildren().add(choices);
				dialog.getDialogPane().setPrefSize(560, 260);
				dialog.getDialogPane().setMinSize(560, 260);
			} else if ("create".equals(state.mode)) {
				titleLabel.setText("Step 3: Create New");
				subtitleLabel.setText("Enter party details");
				GridPane form = new GridPane();
				form.setHgap(10);
				form.setVgap(10);
				if ("organization".equals(state.entityType)) {
					form.add(new Label("Name"), 0, 0);
					form.add(createOrganizationNameField, 1, 0);
					form.add(new Label("Organization Type"), 0, 1);
					form.add(createOrganizationTypeChoice, 1, 1);
					form.add(new Label("Party Role"), 0, 2);
					form.add(roleChoice, 1, 2);
					form.add(new Label("Affiliation"), 0, 3);
					form.add(sideChoice, 1, 3);
				} else {
					form.add(new Label("First Name"), 0, 0);
					form.add(createFirstNameField, 1, 0);
					form.add(new Label("Last Name"), 0, 1);
					form.add(createLastNameField, 1, 1);
					form.add(new Label("Party Role"), 0, 2);
					form.add(roleChoice, 1, 2);
					form.add(new Label("Affiliation"), 0, 3);
					form.add(sideChoice, 1, 3);
				}
				VBox formHost = new VBox(form);
				formHost.setAlignment(Pos.TOP_LEFT);
				formHost.setPadding(new Insets(8, 20, 8, 20));
				contentBox.getChildren().add(formHost);
				dialog.getDialogPane().setPrefSize(720, 420);
				dialog.getDialogPane().setMinSize(720, 420);
			} else {
				titleLabel.setText("Step 3: Select Existing");
				subtitleLabel.setText("Choose an existing contact or organization");
				refreshExistingList.run();
				GridPane relationships = new GridPane();
				relationships.setHgap(10);
				relationships.setVgap(10);
				relationships.add(new Label("Party Role"), 0, 0);
				relationships.add(roleChoice, 1, 0);
				relationships.add(new Label("Affiliation"), 0, 1);
				relationships.add(sideChoice, 1, 1);
				relationships.add(primaryCheck, 1, 2);
				relationships.add(new Label("Notes"), 0, 3);
				relationships.add(notesArea, 1, 3);
				VBox relationshipsHost = new VBox(relationships);
				relationshipsHost.setAlignment(Pos.TOP_LEFT);
				relationshipsHost.setPadding(new Insets(8, 20, 8, 20));
				contentBox.getChildren().addAll(searchField, existingList, relationshipsHost);
				dialog.getDialogPane().setPrefSize(820, 660);
				dialog.getDialogPane().setMinSize(820, 660);
			}
			if (dialog.getDialogPane().getScene() != null && dialog.getDialogPane().getScene().getWindow() != null) {
				dialog.getDialogPane().getScene().getWindow().sizeToScene();
			}
			refreshAddButtonState.run();
		};

		createNewButton.setOnAction(e -> {
			state.mode = "create";
			state.step = 2;
			renderStep.run();
		});
		selectExistingButton.setOnAction(e -> {
			state.mode = "select";
			state.step = 2;
			renderStep.run();
		});
		contactButton.setOnAction(e -> {
			state.entityType = "contact";
			state.step = 3;
			renderStep.run();
		});
		organizationButton.setOnAction(e -> {
			state.entityType = "organization";
			state.step = 3;
			renderStep.run();
		});

		backButton.addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
			e.consume();
			syncStateFromControls.run();
			if (state.step == 3) {
				state.step = 2;
			} else if (state.step == 2) {
				state.step = 1;
			}
			renderStep.run();
		});

		searchField.textProperty().addListener((obs, ov, nv) -> {
			if (state.step == 3 && "select".equals(state.mode)) {
				refreshExistingList.run();
				refreshAddButtonState.run();
			}
		});
		existingList.getSelectionModel().selectedItemProperty().addListener((obs, ov, nv) -> {
			state.selectedEntity = nv;
			refreshAddButtonState.run();
		});
		roleChoice.valueProperty().addListener((obs, ov, nv) -> refreshAddButtonState.run());
		sideChoice.valueProperty().addListener((obs, ov, nv) -> refreshAddButtonState.run());
		createFirstNameField.textProperty().addListener((obs, ov, nv) -> refreshAddButtonState.run());
		createLastNameField.textProperty().addListener((obs, ov, nv) -> refreshAddButtonState.run());
		createOrganizationNameField.textProperty().addListener((obs, ov, nv) -> refreshAddButtonState.run());
		createOrganizationTypeChoice.valueProperty().addListener((obs, ov, nv) -> refreshAddButtonState.run());
		primaryCheck.selectedProperty().addListener((obs, ov, nv) -> refreshAddButtonState.run());
		notesArea.textProperty().addListener((obs, ov, nv) -> refreshAddButtonState.run());

		renderStep.run();

		dialog.setResultConverter(button -> {
			if (button != addType) {
				return null;
			}
			syncStateFromControls.run();
			if (state.partyRoleId == null || state.affiliation == null || state.entityType == null || state.mode == null) {
				return null;
			}
			if ("create".equals(state.mode)) {
				return new AddPartyDraft(
						state.entityType,
						null,
						null,
						state.partyRoleId,
						state.affiliation,
						false,
						null,
						true,
						state.createContactFirstName,
						state.createContactLastName,
						state.createOrganizationName,
						state.createOrganizationTypeId);
			}
			if (state.selectedEntity == null) {
				return null;
			}
			return new AddPartyDraft(
					state.selectedEntity.entityType(),
					state.selectedEntity.id(),
					state.selectedEntity.label(),
					state.partyRoleId,
					state.affiliation,
					state.primary,
					state.notes,
					false,
					null,
					null,
					null,
					null);
		});

		return dialog.showAndWait().orElse(null);
	}

	private static String toPartyRoleLabel(String roleName, long roleId) {
		String normalized = safeText(roleName).trim().replace('_', ' ');
		if (normalized.isBlank()) {
			return "Role " + roleId;
		}
		String[] tokens = normalized.split("\\s+");
		for (int i = 0; i < tokens.length; i++) {
			String token = tokens[i];
			if (token.isBlank()) {
				continue;
			}
			tokens[i] = token.substring(0, 1).toUpperCase(Locale.ROOT) + token.substring(1).toLowerCase(Locale.ROOT);
		}
		return String.join(" ", tokens);
	}

	private static Button asButton(Node node) {
		return node instanceof Button button ? button : null;
	}

	private static void applyToolbarButtonClasses(Button button, String variantClass) {
		if (button == null) {
			return;
		}
		if (!button.getStyleClass().contains("app-toolbar-button")) {
			button.getStyleClass().add("app-toolbar-button");
		}
		if (!button.getStyleClass().contains(variantClass)) {
			button.getStyleClass().add(variantClass);
		}
	}

	private static void setVisibleManaged(Node node, boolean visible) {
		if (node == null)
			return;
		node.setVisible(visible);
		node.setManaged(visible);
	}

	private static String safeText(String value) {
		return value == null ? "" : value.trim();
	}
}
