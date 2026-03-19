package com.shale.ui.component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;

public class OrganizationCard extends HBox {

	private final Label nameLabel = new Label();
	private final Label typeLabel = new Label();
	private final Label phoneLabel = new Label();
	private final Label emailLabel = new Label();
	private final Label websiteLabel = new Label();
	private final Label addressLabel = new Label();
	private final Label notesLabel = new Label();
	private final StackPane avatarHolder = new StackPane();

	private Integer organizationId;
	private Consumer<Integer> onOpen;

	public OrganizationCard() {
		buildUiMiniDefaults();
		wireEvents();
	}

	public void setOrganizationId(Integer organizationId) {
		this.organizationId = organizationId;
	}

	public void setOnOpen(Consumer<Integer> onOpen) {
		this.onOpen = onOpen;
	}

	public void setName(String name) {
		nameLabel.setText(fallback(name));
	}

	public void setOrganizationType(Integer organizationTypeId, String organizationTypeName) {
		String resolvedName = organizationTypeName == null ? "" : organizationTypeName.trim();
		if (!resolvedName.isEmpty()) {
			typeLabel.setText("Type: " + resolvedName);
			return;
		}

		typeLabel.setText(organizationTypeId == null ? "Type: Unknown" : "Type: " + organizationTypeId);
	}

	public void setPhone(String phone) {
		phoneLabel.setText("Phone: " + fallback(phone));
	}

	public void setEmail(String email) {
		emailLabel.setText("Email: " + fallback(email));
	}

	public void setWebsite(String website) {
		websiteLabel.setText("Web: " + fallback(website));
	}

	public void setAddress(String address1, String address2, String city, String state, String postalCode, String country) {
		List<String> parts = new ArrayList<>();
		String street = joinNonBlank(", ", address1, address2);
		String region = joinNonBlank(", ", city, state);
		String postalAndCountry = joinNonBlank(" ", postalCode, country);

		if (!street.isBlank()) {
			parts.add(street);
		}
		if (!region.isBlank()) {
			parts.add(region);
		}
		if (!postalAndCountry.isBlank()) {
			parts.add(postalAndCountry);
		}

		String oneLineAddress = parts.isEmpty() ? "—" : String.join(" • ", parts);
		addressLabel.setText("Address: " + oneLineAddress);
	}

	public void setNotesSnippet(String notes) {
		String trimmed = notes == null ? "" : notes.trim();
		if (trimmed.isEmpty()) {
			notesLabel.setText("");
			return;
		}

		String compact = trimmed.replaceAll("\\s+", " ");
		int max = 96;
		if (compact.length() > max) {
			compact = compact.substring(0, max - 1).trim() + "…";
		}
		notesLabel.setText("Notes: " + compact);
	}

	public void setBackgroundCssColor(String css) {
		setStyle(CardSurfaceStyles.cardContainerStyle(css));
	}

	public void applyMini() {
		getChildren().clear();

		setPadding(new Insets(4, 10, 4, 10));
		setSpacing(6);

		nameLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: 600;");
		getChildren().add(nameLabel);
	}

	public void applyCompact() {
		getChildren().clear();

		setPadding(new Insets(8, 10, 8, 10));
		setSpacing(8);

		Node avatar = buildAvatar(18);

		nameLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: 600;");
		typeLabel.setStyle("-fx-font-size: 11px; -fx-opacity: 0.78;");
		phoneLabel.setStyle("-fx-font-size: 11px; -fx-opacity: 0.78;");

		VBox text = new VBox(1, nameLabel, typeLabel, phoneLabel);
		getChildren().addAll(avatar, text);
	}

	public void applyFull() {
		getChildren().clear();

		setPadding(new Insets(10, 12, 10, 12));
		setSpacing(10);

		Node avatar = buildAvatar(26);

		nameLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: 700;");
		typeLabel.setStyle("-fx-font-size: 12px; -fx-opacity: 0.82;");
		phoneLabel.setStyle("-fx-font-size: 12px; -fx-opacity: 0.82;");
		emailLabel.setStyle("-fx-font-size: 12px; -fx-opacity: 0.82;");
		websiteLabel.setStyle("-fx-font-size: 12px; -fx-opacity: 0.82;");
		addressLabel.setStyle("-fx-font-size: 12px; -fx-opacity: 0.82;");
		notesLabel.setStyle("-fx-font-size: 11px; -fx-opacity: 0.72;");

		VBox text = new VBox(2, nameLabel, typeLabel, phoneLabel, emailLabel, websiteLabel, addressLabel);
		if (!notesLabel.getText().isBlank()) {
			text.getChildren().add(notesLabel);
		}

		getChildren().addAll(avatar, text);
	}

	public Node asNode() {
		return this;
	}

	private void buildUiMiniDefaults() {
		setCursor(Cursor.HAND);
		setBackgroundCssColor(null);
		applyMini();
	}

	private void wireEvents() {
		setOnMouseClicked(e -> {
			if (onOpen != null && organizationId != null) {
				onOpen.accept(organizationId);
			}
		});
	}

	private Node buildAvatar(double radius) {
		Circle c = new Circle(radius);
		c.setStyle("-fx-fill: rgba(255,255,255,0.55); -fx-stroke: rgba(0,0,0,0.10);");
		avatarHolder.getChildren().setAll(c);
		return avatarHolder;
	}

	private static String fallback(String value) {
		return value == null || value.isBlank() ? "—" : value;
	}

	private static String joinNonBlank(String separator, String... values) {
		List<String> filtered = new ArrayList<>();
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				filtered.add(value.trim());
			}
		}
		return String.join(separator, filtered);
	}
}
