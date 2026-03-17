package com.shale.ui.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.shale.core.model.Organization;
import com.shale.ui.component.factory.OrganizationCardFactory;
import com.shale.ui.component.factory.OrganizationCardFactory.OrganizationCardModel;
import com.shale.ui.services.UiRuntimeBridge;
import com.shale.ui.state.AppState;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;

public final class OrganizationsController {

	@FXML
	private TextField organizationsSearchField;
	@FXML
	private ScrollPane organizationsScroll;
	@FXML
	private FlowPane organizationsFlow;
	@FXML
	private Label organizationsEmptyStateLabel;

	private AppState appState;
	private UiRuntimeBridge runtimeBridge;

	private OrganizationCardFactory organizationCardFactory;
	private final List<Organization> loaded = new ArrayList<>();

	public void init(AppState appState, UiRuntimeBridge runtimeBridge) {
		this.appState = appState;
		this.runtimeBridge = runtimeBridge;
		this.organizationCardFactory = new OrganizationCardFactory(this::openOrganization);
	}

	@FXML
	private void initialize() {
		if (organizationsSearchField != null) {
			organizationsSearchField.textProperty().addListener((obs, oldV, newV) -> rerender());
		}

		loadInitialOrganizations();
		rerender();
	}

	private void loadInitialOrganizations() {
		loaded.clear();
		loaded.addAll(loadMockOrganizations());
	}

	private List<Organization> loadMockOrganizations() {
		// Keep empty by default in this step.
		// Replace with DAO/paged loading in next step.
		return List.of();
	}

	private void rerender() {
		if (organizationsFlow == null) {
			return;
		}

		String q = normalizedQuery();
		List<Organization> filtered = loaded.stream()
				.filter(org -> matches(org, q))
				.toList();

		List<Node> cards = filtered.stream()
				.map(this::buildCard)
				.toList();

		organizationsFlow.getChildren().setAll(cards);
		updateEmptyState(filtered.isEmpty());
	}

	private Node buildCard(Organization org) {
		return organizationCardFactory.create(toModel(org), OrganizationCardFactory.Variant.FULL);
	}

	private OrganizationCardModel toModel(Organization org) {
		return new OrganizationCardModel(
				org.getId(),
				org.getName(),
				org.getOrganizationTypeId(),
				org.getPhone(),
				org.getEmail(),
				org.getWebsite(),
				org.getAddress1(),
				org.getAddress2(),
				org.getCity(),
				org.getState(),
				org.getPostalCode(),
				org.getCountry(),
				org.getNotes(),
				null);
	}

	private void updateEmptyState(boolean empty) {
		if (organizationsEmptyStateLabel != null) {
			organizationsEmptyStateLabel.setVisible(empty);
			organizationsEmptyStateLabel.setManaged(empty);
		}
		if (organizationsScroll != null) {
			organizationsScroll.setVisible(!empty);
			organizationsScroll.setManaged(!empty);
		}
	}

	private String normalizedQuery() {
		if (organizationsSearchField == null || organizationsSearchField.getText() == null) {
			return "";
		}
		return organizationsSearchField.getText().trim().toLowerCase(Locale.ROOT);
	}

	private static boolean matches(Organization org, String query) {
		if (query == null || query.isBlank()) {
			return true;
		}

		return contains(org.getName(), query)
				|| contains(org.getEmail(), query)
				|| contains(org.getPhone(), query)
				|| contains(org.getWebsite(), query)
				|| contains(org.getCity(), query)
				|| contains(org.getState(), query)
				|| contains(org.getCountry(), query);
	}

	private static boolean contains(String value, String query) {
		return value != null && value.toLowerCase(Locale.ROOT).contains(query);
	}

	private void openOrganization(Integer organizationId) {
		if (organizationId == null) {
			return;
		}
		System.out.println("Navigate to Organization: " + organizationId);
	}
}
