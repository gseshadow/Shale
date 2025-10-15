package GUIElements;

import application.Main;
import dataStructures.Facility;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.layout.FlowPane;
import tools.FacilityBuilder;

public class FacilitiesView extends FlowPane {

	public FacilitiesView() {

		this.setPadding(new Insets(10));
		this.setHgap(10);
		this.setVgap(10);

		this.setAlignment(Pos.CENTER);

		for (Facility f : Main.getFacilities().values()) {
			this.getChildren().add(new FacilityBuilder(f));
		}

	}

}
