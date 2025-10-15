package tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import application.Main;
import dataStructures.Facility;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

public class FacilityBuilder extends VBox {
	private String name;
	private String description;
	private String phone;
	private String fax;
	private String acronym;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getPhone() {
		return phone;
	}

	public void setPhone(String phone) {
		this.phone = phone;
	}

	public String getFax() {
		return fax;
	}

	public void setFax(String fax) {
		this.fax = fax;
	}

	public String getAcronym() {
		return acronym;
	}

	public void setAcronym(String acronym) {
		this.acronym = acronym;
	}

	public FacilityBuilder(Facility f) {
		if (Main.isGlobalDebug()) {
			System.out.println();
			System.out.println("FacilityBuilder(): " + f.getName());
			try {
				Files.write(Paths.get(Main.getLog().getAbsolutePath()), "\nLoading Cases".getBytes(), StandardOpenOption.APPEND);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		this.setBackground(new Background(new BackgroundFill(Color.GRAY, new CornerRadii(3), new Insets(1))));
		this.setSpacing(10);
		this.setPadding(new Insets(10));

		name = f.getName();
		description = f.getDescription();
		phone = f.getPhone();
		fax = f.getFax();
		acronym = f.getAcronym();

		Label lname = new Label("Facility Name: " + name);
		this.getChildren().add(lname);

		Label ldescription = new Label("Description: " + description);
		this.getChildren().add(ldescription);

		Label lphone = new Label("Phone: " + phone);
		this.getChildren().add(lphone);

		Label lfax = new Label("Fax: " + fax);
		this.getChildren().add(lfax);

		Label lacronym = new Label("Acronym: " + acronym);
		this.getChildren().add(lacronym);
	}

}
