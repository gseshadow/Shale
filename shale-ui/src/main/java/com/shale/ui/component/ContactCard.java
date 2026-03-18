package com.shale.ui.component;

import java.util.function.Consumer;

import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public final class ContactCard extends StackPane {

	public enum Variant {
		MINI, COMPACT, FULL
	}

	private final Label nameLabel = new Label();

	public ContactCard(Integer contactId, String displayName, Variant variant, Consumer<Integer> onOpen) {
		setAlignment(Pos.CENTER_LEFT);

		// ✅ IMPORTANT: use a real paint value (not -color-bg-subtle) to avoid ClassCastException
		// Also make it "pill" shaped like your status/user mini chips.
		setStyle("-fx-background-radius: 999; "
				+ "-fx-padding: 4 10 4 10; "
				+ "-fx-background-color: rgba(0,0,0,0.03);");

		String text = (displayName == null || displayName.isBlank()) ? "—" : displayName;
		nameLabel.setText(text);

		if (variant == Variant.MINI) {
			nameLabel.setStyle("-fx-font-size: 12;");
		} else if (variant == Variant.COMPACT) {
			nameLabel.setStyle("-fx-font-size: 13;");
		} else {
			nameLabel.setStyle("-fx-font-size: 14;");
		}

		VBox box = new VBox(nameLabel);
		getChildren().add(box);

		if (contactId != null && onOpen != null) {
			setCursor(Cursor.HAND);
			setOnMouseClicked(e -> onOpen.accept(contactId));
		}
	}
}
