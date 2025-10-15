package GUIElements;

import dataStructures.Facility;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

public class FacilityPane extends FlowPane {

	private TextField name;
	private TextField description;
	private TextField phone;
	private TextField fax;
	private TextField acronym;

	public FacilityPane(Facility facility) {
		name.setText(facility.getName());
		description.setText(facility.getDescription());
		phone.setText(facility.getPhone());
		fax.setText(facility.getFax());
		acronym.setText(facility.getAcronym());

		VBox detailsBox = new VBox();
		detailsBox.setPadding(new Insets(10));
		detailsBox.setSpacing(10);

		Label title = new Label("Details");
		detailsBox.getChildren().add(title);

		Label nameLabel = new Label("Facility Name");
		detailsBox.getChildren().add(nameLabel);
		detailsBox.getChildren().add(name);

		Label acronymLabel = new Label("Acronym");
		detailsBox.getChildren().add(acronymLabel);
		detailsBox.getChildren().add(acronym);

		Label descriptionLabel = new Label("Description");
		detailsBox.getChildren().add(descriptionLabel);
		detailsBox.getChildren().add(descriptionLabel);

		Label phoneLabel = new Label("Phone");
		detailsBox.getChildren().add(phoneLabel);
		detailsBox.getChildren().add(phone);

		Label faxLabel = new Label("Fax");
		detailsBox.getChildren().add(faxLabel);
		detailsBox.getChildren().add(fax);

	}

}
