package com.shale.ui.component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
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
	private String backgroundCss;
	private boolean hovered;
	private boolean suppressPlaceholderLines;

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
		backgroundCss = css;
		refreshSurfaceStyle();
	}

	public void setSuppressPlaceholderLines(boolean suppressPlaceholderLines) {
		this.suppressPlaceholderLines = suppressPlaceholderLines;
	}

	public void applyMini() {
		getChildren().clear();

		setPrefWidth(javafx.scene.layout.Region.USE_COMPUTED_SIZE);
		setMaxWidth(javafx.scene.layout.Region.USE_COMPUTED_SIZE);
		setPadding(new Insets(4, 10, 4, 10));
		setSpacing(6);

		nameLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: 600;");
		getChildren().add(nameLabel);
	}

	public void applyCompact() {
		getChildren().clear();

		setAlignment(Pos.TOP_LEFT);
		setPadding(new Insets(10, 12, 10, 12));
		setSpacing(12);

		Node avatar = buildAvatar(18);

		nameLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: 700; -fx-text-fill: #112542;");
		typeLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: 600; -fx-text-fill: rgba(17,37,66,0.62);");
		phoneLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: rgba(17,37,66,0.74);");

		VBox text = new VBox(4, nameLabel);
		if (!(suppressPlaceholderLines && "Type: Unknown".equals(typeLabel.getText()))) {
			text.getChildren().add(typeLabel);
		}
		if (!(suppressPlaceholderLines && "Phone: —".equals(phoneLabel.getText()))) {
			text.getChildren().add(phoneLabel);
		}
		getChildren().addAll(avatar, text);
	}

	public void applyFull() {
		getChildren().clear();

		setAlignment(Pos.TOP_LEFT);
		setMinWidth(320);
		setPrefWidth(340);
		setMaxWidth(340);
		setPadding(new Insets(14, 16, 14, 16));
		setSpacing(14);

		Node avatar = buildAvatar(28);

		nameLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: 700; -fx-text-fill: #112542;");
		typeLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: 600; -fx-text-fill: rgba(17,37,66,0.62);");
		phoneLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: 600; -fx-text-fill: rgba(17,37,66,0.82);");
		emailLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: rgba(17,37,66,0.76);");
		websiteLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: rgba(17,37,66,0.74);");
		addressLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: rgba(17,37,66,0.72);");
		notesLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: rgba(17,37,66,0.66);");
		emailLabel.setWrapText(true);
		websiteLabel.setWrapText(true);
		addressLabel.setWrapText(true);
		notesLabel.setWrapText(true);

		VBox text = new VBox(5, nameLabel, typeLabel, phoneLabel, emailLabel, websiteLabel, addressLabel);
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
		setOnMouseEntered(e -> {
			hovered = true;
			setTranslateY(-1.5);
			refreshSurfaceStyle();
		});
		setOnMouseExited(e -> {
			hovered = false;
			setTranslateY(0);
			refreshSurfaceStyle();
		});
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

	private void refreshSurfaceStyle() {
		setStyle(CardSurfaceStyles.cardContainerStyle(backgroundCss, hovered));
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
