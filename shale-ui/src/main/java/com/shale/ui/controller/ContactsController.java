package com.shale.ui.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import com.shale.data.dao.ContactDao;
import com.shale.ui.component.factory.ContactCardFactory;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

public final class ContactsController {

	@FXML
	private TextField contactsSearchField;
	@FXML
	private ScrollPane contactsScroll;
	@FXML
	private FlowPane contactsFlow;
	@FXML
	private Label contactsEmptyStateLabel;

	private ContactDao contactDao;
	private ContactCardFactory contactCardFactory;
	private Consumer<Integer> onOpenContact;

	private int currentPage = 0;
	private final int pageSize = 100;
	private boolean loading = false;
	private boolean hasMore = true;
	private int loadGeneration = 0;

	private final List<ContactDao.ContactRow> loaded = new ArrayList<>();

	private final ExecutorService dbExec = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "contacts-loader");
		t.setDaemon(true);
		return t;
	});

	public void init(ContactDao contactDao, Consumer<Integer> onOpenContact) {
		this.contactDao = contactDao;
		this.onOpenContact = onOpenContact;
		this.contactCardFactory = new ContactCardFactory(this::openContact);
	}

	@FXML
	private void initialize() {
		if (contactsSearchField != null) {
			contactsSearchField.textProperty().addListener((obs, oldV, newV) -> loadFirstPage());
		}

		Platform.runLater(() -> {
			if (contactDao == null) {
				updateEmptyState(true);
				return;
			}
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
		loaded.clear();

		if (contactsFlow != null) {
			contactsFlow.getChildren().clear();
		}

		updateEmptyState(false);
		loadNextPage();
	}

	private void loadNextPage() {
		if (loading || !hasMore || contactDao == null) {
			return;
		}

		loading = true;
		final int pageToLoad = currentPage;
		final int generationAtSubmit = loadGeneration;
		final String search = normalizedQuery();

		dbExec.submit(() -> {
			try {
				ContactDao.PagedResult<ContactDao.ContactRow> page = contactDao.findPage(pageToLoad, pageSize, search);
				Platform.runLater(() -> {
					if (generationAtSubmit != loadGeneration) {
						loading = false;
						return;
					}

					loaded.addAll(page.items());
					currentPage++;
					hasMore = loaded.size() < page.total();
					loading = false;
					rerender();
				});
			} catch (Exception ex) {
				Platform.runLater(() -> {
					if (generationAtSubmit != loadGeneration) {
						return;
					}
					loading = false;
					updateEmptyState(loaded.isEmpty());
				});
			}
		});
	}

	private void rerender() {
		if (contactsFlow == null) {
			return;
		}

		List<Node> cards = loaded.stream()
				.map(this::buildCard)
				.toList();

		contactsFlow.getChildren().setAll(cards);
		updateEmptyState(cards.isEmpty());
	}

	private Node buildCard(ContactDao.ContactRow row) {
		VBox container = new VBox(6);
		container.getChildren().add(contactCardFactory.createCompact(row.id(), row.displayName()));

		Label subtext = new Label(composeSubtext(row));
		subtext.setWrapText(true);
		subtext.setStyle("-fx-opacity: 0.75;");
		container.getChildren().add(subtext);

		container.getStyleClass().add("secondary-panel");
		container.setPrefWidth(280);
		return container;
	}

	private static String composeSubtext(ContactDao.ContactRow row) {
		List<String> parts = new ArrayList<>(2);
		if (row.email() != null && !row.email().isBlank()) {
			parts.add(row.email().trim());
		}
		if (row.phone() != null && !row.phone().isBlank()) {
			parts.add(row.phone().trim());
		}
		if (parts.isEmpty()) {
			return "No email or phone";
		}
		return String.join(" • ", parts);
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

	private String normalizedQuery() {
		if (contactsSearchField == null || contactsSearchField.getText() == null) {
			return "";
		}
		return contactsSearchField.getText().trim();
	}

	private void openContact(Integer contactId) {
		if (contactId == null) {
			return;
		}
		if (onOpenContact != null) {
			onOpenContact.accept(contactId);
		}
	}
}
