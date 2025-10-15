package GUIElements;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

import application.Main;
import application.util.ResourceManager;
import connections.ConnectionResources;
import connections.Server;
import dataStructures.Facility;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class MultiPicker extends BorderPane {

	HBox containerA = new HBox();

	HashMap<Integer, Facility> unselectedMap = new HashMap<Integer, Facility>();
	ListView<Facility> unselectedList = new ListView<Facility>();

	HashMap<Integer, Facility> selectedMap = new HashMap<Integer, Facility>();
	ListView<Facility> selectedList = new ListView<Facility>();

	FlowPane listPane = new FlowPane();

	HBox containerB = new HBox();
	TextField searchBox = new TextField();
	String searchCriteria = "";
	Button addNew = new Button("Quick Add");

	FlowPane quickAddPane = new FlowPane();
	TextField facilityName = new TextField();
	TextField facilityAcronym = new TextField();
	TextField facilityPhoneNumber = new TextField();
	TextField facilityDescription = new TextField();
	int newId = 0;

	HBox containerC = new HBox();
	Button closeList = new Button("Close");

	HBox containerD = new HBox();
	Button saveFacility = new Button("Save");
	Button cancelFacility = new Button("Cancel");

	BorderPane parent = new BorderPane();

	public MultiPicker(int type, ArrayList<Integer> selectedIds, IntakePane intake) {
		this.setPrefSize(700, 500);
		parent = this;

		/*
		 * Setup lists
		 */
		for (Facility f : Main.getFacilities().values()) {
			if (selectedIds.contains(f.get_id())) {
				selectedMap.put(f.get_id(), f);
			} else {
				unselectedMap.put(f.get_id(), f);
			}
		}
		loadList(selectedMap, selectedList);
		loadList(unselectedMap, unselectedList);

		/*
		 * Setup listPane
		 */
		listPane.setAlignment(Pos.CENTER);
		listPane.setHgap(10);
		listPane.setVgap(10);
		listPane.setPadding(new Insets(10));

		containerA.setSpacing(10);

		VBox uns = new VBox();
		uns.setSpacing(10);
		uns.getChildren().add(new Label("Unselected"));
		uns.getChildren().add(unselectedList);
		containerA.getChildren().add(uns);

		VBox sel = new VBox();
		sel.setSpacing(10);
		sel.getChildren().add(new Label("Selected"));
		sel.getChildren().add(selectedList);
		containerA.getChildren().add(sel);

		listPane.getChildren().add(containerA);

		this.setCenter(listPane);

		containerB.setSpacing(10);
		containerB.setAlignment(Pos.CENTER);
		containerB.setPadding(new Insets(10));

		containerB.getChildren().add(new Label("Search:"));
		containerB.getChildren().add(searchBox);
		containerB.getChildren().add(addNew);
		this.setTop(containerB);

		containerC.setSpacing(10);
		containerC.setAlignment(Pos.CENTER_RIGHT);
		containerC.setPadding(new Insets(10));

		containerC.getChildren().add(closeList);
		this.setBottom(containerC);

		containerD.setSpacing(10);
		containerD.setAlignment(Pos.CENTER_RIGHT);
		containerD.setPadding(new Insets(10));

		containerD.getChildren().add(saveFacility);
		containerD.getChildren().add(cancelFacility);

		/*
		 * Setup Search Box
		 */
		searchBox.setOnKeyTyped(new EventHandler<Event>() {

			@Override
			public void handle(Event arg0) {
				searchCriteria = searchBox.getText().toLowerCase();
				loadList(selectedMap, selectedList);
				loadList(unselectedMap, unselectedList);
			}

		});

		/*
		 * Setup new window
		 */
		Stage stage = new Stage();
		Scene scene = new Scene(this);
                stage.getIcons().add(ResourceManager.loadImage("ShaleNoText.png"));
		stage.setScene(scene);
		stage.show();

		/*
		 * Setup Buttons
		 */

		closeList.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				intake.updateFacilities();
				stage.close();

			}
		});

		addNew.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {

				newId = Server.createNewFacility(Main.getCurrentCase().getCaseOrganizationId(), ConnectionResources.getConnection());

				facilityName.setText(searchBox.getText());
				parent.setCenter(quickAddPane);
				parent.setTop(null);
				parent.setBottom(containerD);
			}
		});

		/*
		 * Setup quickadd pane
		 */
		facilityName.setPromptText("Facility Name");
		facilityAcronym.setPromptText("Acronym ie: 'UMNH'");
		facilityPhoneNumber.setPromptText("Phone Number");

		VBox facilityBox = new VBox();
		facilityBox.setSpacing(10);
		facilityBox.setPadding(new Insets(10));

		facilityBox.getChildren().add(new Label("Name"));
		facilityBox.getChildren().add(facilityName);
		facilityBox.getChildren().add(new Label("Acronym"));
		facilityBox.getChildren().add(facilityAcronym);
		facilityBox.getChildren().add(new Label("Phone"));
		facilityBox.getChildren().add(facilityPhoneNumber);
		facilityBox.getChildren().add(new Label("Description"));
		facilityBox.getChildren().add(facilityDescription);

		quickAddPane.getChildren().add(facilityBox);
		quickAddPane.setAlignment(Pos.CENTER);

		saveFacility.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {

				Facility temp = new Facility();
				temp.set_id(newId);
				temp.setName(facilityName.getText());
				temp.setDescription(facilityDescription.getText());
				temp.setPhone(facilityPhoneNumber.getText());
				temp.setAcronym(facilityAcronym.getText());

				searchCriteria = "";
				searchBox.setText("");

				Main.getFacilities().put(newId, temp);
				selectedMap.put(newId, temp);

				loadList(selectedMap, selectedList);
				loadList(unselectedMap, unselectedList);

				parent.setCenter(listPane);
				parent.setTop(containerB);
				parent.setBottom(containerC);

				Task<Integer> t = new Task<Integer>() {

					@Override
					protected Integer call() throws Exception {
						Server.createNewCaseFacility(Main.getCurrentCaseId(), newId, ConnectionResources.getConnection());
						Server.updateFacilityStringField(newId, "name", facilityName.getText(), ConnectionResources.getConnection());
						Server.updateFacilityStringField(newId, "description", facilityDescription.getText(), ConnectionResources.getConnection());
						Server.updateFacilityStringField(newId, "acronym", facilityAcronym.getText(), ConnectionResources.getConnection());
						Server.updateFacilityStringField(newId, "phone", facilityPhoneNumber.getText(), ConnectionResources.getConnection());
						return null;
					}
				};
				Platform.runLater(new Thread(t));
			}
		});

		cancelFacility.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {

				parent.setCenter(listPane);
				parent.setTop(containerB);
				parent.setBottom(containerC);

			}
		});
	}

	private void loadList(HashMap<Integer, Facility> source, ListView<Facility> destination) {
		/*
		 * Add selected facilities into the selected VBox
		 */
		destination.getItems().clear();
		for (Facility f : source.values()) {
			if (!searchCriteria.equals("")) {
				if (f.getName().toLowerCase().contains(searchCriteria) || f.getAcronym().toLowerCase().contains(searchCriteria)) {
					addToList(destination, f);
				}
			} else {
				addToList(destination, f);
			}
		}

	}

	private void addToList(ListView<Facility> destination, Facility f) {
		f.setOnMouseClicked(new EventHandler<Event>() {

			@Override
			public void handle(Event arg0) {
				if (selectedMap.values().contains(f)) {
					unselectedMap.put(f.get_id(), f);
					selectedMap.values().remove(f);

					new Thread(new Task<Integer>() {

						@Override
						protected Integer call() throws Exception {
							Server.deleteCaseFacility(Main.getCurrentCaseId(), f.get_id(), ConnectionResources.getConnection());
							return null;
						}
					}).start();

				} else {
					selectedMap.put(f.get_id(), f);
					unselectedMap.values().remove(f);
					new Thread(new Task<Integer>() {

						@Override
						protected Integer call() throws Exception {
							Server.createNewCaseFacility(Main.getCurrentCaseId(), f.get_id(), ConnectionResources.getConnection());
							return null;
						}
					}).start();
				}
				loadList(selectedMap, selectedList);
				loadList(unselectedMap, unselectedList);
			}
		});
		destination.getItems().add(f);

		Collections.sort(destination.getItems(), new Comparator<Facility>() {

			@Override
			public int compare(Facility o1, Facility o2) {
				return o1.getName().toLowerCase().compareTo(o2.getName().toLowerCase());
			}
		});
	}

}
