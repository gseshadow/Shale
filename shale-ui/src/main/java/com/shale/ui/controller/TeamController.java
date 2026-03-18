package com.shale.ui.controller;

import com.shale.data.dao.UserDao;
import com.shale.data.dao.UserDao.DirectoryUserRow;
import com.shale.ui.component.factory.UserCardFactory;
import com.shale.ui.component.factory.UserCardFactory.UserCardModel;
import com.shale.ui.component.factory.UserCardFactory.Variant;
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

public final class TeamController {

	@FXML
	private TextField teamSearchField;
	@FXML
	private ScrollPane teamScroll;
	@FXML
	private FlowPane teamFlow;
	@FXML
	private Label teamEmptyStateLabel;

	private AppState appState;
	private UserDao userDao;
	private UserCardFactory userCardFactory;
	private List<DirectoryUserRow> loadedUsers = List.of();
	private String emptyStateMessage = "No team members to display yet.";
	private int loadGeneration = 0;

	private final ExecutorService dbExec = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "team-directory-loader");
		t.setDaemon(true);
		return t;
	});

	public void init(AppState appState, UserDao userDao, Consumer<Integer> onOpenUser) {
		this.appState = appState;
		this.userDao = userDao;
		this.userCardFactory = new UserCardFactory(onOpenUser == null ? id -> {
		} : onOpenUser);
	}

	@FXML
	private void initialize() {
		if (teamSearchField != null) {
			teamSearchField.textProperty().addListener((obs, oldV, newV) -> rerender());
		}

		Platform.runLater(this::loadUsers);
	}

	private void loadUsers() {
		loadGeneration++;
		final int generationAtSubmit = loadGeneration;

		if (userDao == null) {
			loadedUsers = List.of();
			setEmptyStateMessage("Team is unavailable right now.");
			rerender();
			return;
		}

		Integer tenantId = appState == null ? null : appState.getShaleClientId();
		if (tenantId == null || tenantId <= 0) {
			loadedUsers = List.of();
			setEmptyStateMessage("No tenant is selected.");
			rerender();
			return;
		}

		dbExec.submit(() -> {
			try {
				List<DirectoryUserRow> users = new ArrayList<>(userDao.listUsersForTenant(tenantId));
				users.sort(Comparator.comparing((DirectoryUserRow row) -> safe(row.displayName()), String.CASE_INSENSITIVE_ORDER)
						.thenComparingInt(DirectoryUserRow::id));

				Platform.runLater(() -> {
					if (generationAtSubmit != loadGeneration) {
						return;
					}
					loadedUsers = List.copyOf(users);
					setEmptyStateMessage("No team members to display yet.");
					rerender();
				});
			} catch (RuntimeException ex) {
				Platform.runLater(() -> {
					if (generationAtSubmit != loadGeneration) {
						return;
					}
					loadedUsers = List.of();
					setEmptyStateMessage("Unable to load team members.");
					rerender();
				});
			}
		});
	}

	private void rerender() {
		if (teamFlow == null) {
			return;
		}

		List<DirectoryUserRow> filteredUsers = loadedUsers.stream()
				.filter(Objects::nonNull)
				.filter(this::matchesSearch)
				.toList();

		List<Node> cards = filteredUsers.stream()
				.map(this::buildCard)
				.toList();

		teamFlow.getChildren().setAll(cards);

		boolean empty = cards.isEmpty();
		String query = normalizedQuery();
		if (empty && !query.isBlank() && !loadedUsers.isEmpty()) {
			setEmptyStateMessage("No team members match your search.");
		} else if (empty && loadedUsers.isEmpty()) {
			setEmptyStateMessage(emptyStateMessage);
		}
		updateEmptyState(empty);
	}

	private Node buildCard(DirectoryUserRow row) {
		String displayName = safe(row.displayName()).isBlank() ? "—" : safe(row.displayName());
		var card = userCardFactory.create(new UserCardModel(
				row.id(),
				displayName,
				row.color(),
				row.initials()), Variant.FULL);
		card.setPrefWidth(260);
		card.setMaxWidth(260);
		return card;
	}

	private boolean matchesSearch(DirectoryUserRow row) {
		String query = normalizedQuery();
		if (query.isBlank()) {
			return true;
		}

		String displayName = safe(row.displayName()).toLowerCase(Locale.ROOT);
		String email = safe(row.email()).toLowerCase(Locale.ROOT);
		return displayName.contains(query) || email.contains(query);
	}

	private void updateEmptyState(boolean empty) {
		if (teamEmptyStateLabel != null) {
			teamEmptyStateLabel.setVisible(empty);
			teamEmptyStateLabel.setManaged(empty);
		}
		if (teamScroll != null) {
			teamScroll.setVisible(!empty);
			teamScroll.setManaged(!empty);
		}
	}

	private void setEmptyStateMessage(String message) {
		emptyStateMessage = safe(message);
		if (teamEmptyStateLabel != null) {
			teamEmptyStateLabel.setText(emptyStateMessage);
		}
	}

	private String normalizedQuery() {
		if (teamSearchField == null || teamSearchField.getText() == null) {
			return "";
		}
		return teamSearchField.getText().trim().toLowerCase(Locale.ROOT);
	}

	private static String safe(String value) {
		return value == null ? "" : value;
	}
}
