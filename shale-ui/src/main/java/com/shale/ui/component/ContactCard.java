package com.shale.ui.component;

import java.util.function.Consumer;

import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class ContactCard extends StackPane {

	public enum Variant {
		MINI, COMPACT, FULL
	}

	private final Label nameLabel = new Label();
	private final Integer contactId;
	private Consumer<Integer> onOpen;

	public ContactCard(Integer contactId, String displayName, Variant variant) {
		this.contactId = contactId;

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

		if (contactId != null) {
			setCursor(Cursor.HAND);
			setOnMouseClicked(e ->
			{
				if (onOpen != null) {
					onOpen.accept(contactId);
				}
			});
		}
	}

	public void setOnOpen(Consumer<Integer> onOpen) {
		this.onOpen = onOpen;
	}
}