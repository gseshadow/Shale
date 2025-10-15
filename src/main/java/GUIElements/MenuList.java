package GUIElements;

import application.Main;
import dataStructures.Case;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import tools.IO;

public class MenuList extends BorderPane {

	private Button newIntake = new Button("New Intake");
	private Button back = new Button("Back");
	private Button viewChange = new Button("Show Active");

	private Button cases = new Button("Cases");
	private Button tasks = new Button("Tasks");
	private Button profile = new Button("My Profile");
	private Button facilities = new Button("Facilities");

	private final int BUTTONHEIGHT = 30;
	private final int BUTTONWIDTH = 150;

	public MenuList(boolean showBack, boolean activeView) {
		if (Main.getCurrentUser().isShowActiveView())
			viewChange.setText("Show Potentials");
		else
			viewChange.setText("Show Active");

		VBox buttonBox = new VBox();
		buttonBox.setSpacing(10);
		buttonBox.setPadding(new Insets(10));
		buttonBox.setAlignment(Pos.TOP_CENTER);

		if (activeView) {
			cases.setPrefSize(BUTTONWIDTH, BUTTONHEIGHT);
//			buttonBox.getChildren().add(cases); //TODO restore when changing to active view

			tasks.setPrefSize(BUTTONWIDTH, BUTTONHEIGHT);
//			buttonBox.getChildren().add(tasks);//TODO restore when changing to active view

			profile.setPrefSize(BUTTONWIDTH, BUTTONHEIGHT);
//			buttonBox.getChildren().add(profile);//TODO restore when changing to active view

			facilities.setPrefSize(BUTTONWIDTH, BUTTONHEIGHT);
			buttonBox.getChildren().add(facilities);

		} else {
			newIntake.setPrefSize(BUTTONWIDTH, BUTTONHEIGHT);
			buttonBox.getChildren().add(newIntake);
		}
		if (showBack)
			viewChange.setVisible(false);
		else
			viewChange.setVisible(true);

		back.setPrefSize(BUTTONWIDTH, BUTTONHEIGHT);
		back.setVisible(showBack);
		newIntake.setVisible(!showBack);
		buttonBox.getChildren().add(back);

		viewChange.setPrefSize(BUTTONWIDTH, BUTTONHEIGHT);
		buttonBox.getChildren().add(viewChange);

		newIntake.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {

				Main.getController().changeLeft(null);
				IntakePane temp = new IntakePane(new Case(), true);
				Main.setIntake(temp);
				Main.getController().changeTop(new TopBar(0, temp));
				Main.getLoading().setNotification("Setting up new Case...");
				Main.getController().changeCenter(Main.getLoading());

			}
		});

		back.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				if (Main.getCurrentUser().isShowActiveView()) {
					Main.getController().changeTop(null);
					Main.getController().changeLeft(false);
					Main.getController().changeCenter(new ActiveCaseView());
				} else {
					Main.getController().changeTop(null);
					Main.getController().changeLeft(false);
					if (Main.getCurrentUser().isShowAsList()) {
						Main.getController().changeCenter(new PotentialsList());
					} else
						Main.getController().changeCenter(new PotentialsBubbles());
				}

			}
		});

		viewChange.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {

				if (viewChange.getText().equals("Show Active")) {
					Main.getCurrentUser().setShowActiveView(true);
					Main.getController().changeTop(null);
					Main.getController().changeCenter(new ActiveCaseView());
					Main.getController().changeTop(null);
					Main.getController().changeLeft(false);
				} else {

					Main.getCurrentUser().setShowActiveView(false);
					if (Main.getCurrentUser().isShowAsList()) {
						Main.getController().changeCenter(new PotentialsList());
					} else {
						Main.getController().changeCenter(new PotentialsBubbles());
						Main.getCurrentUser().setShowActiveView(showBack);
					}
					Main.getController().changeTop(null);
					Main.getController().changeLeft(false);
				}
			}
		});

		facilities.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {

				Main.getController().changeCenter(new FacilitiesView());
				Main.getController().changeTop(null);
				Main.getController().changeLeft(false);
			}
		});

		VBox settingsBox = new VBox();
		settingsBox.setSpacing(10);
		settingsBox.setPadding(new Insets(10));
		settingsBox.setAlignment(Pos.TOP_CENTER);

		Button settings = new Button("Settings");
		settings.setPrefSize(BUTTONWIDTH, BUTTONHEIGHT);
		settings.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				Main.getController().changeTop(null);
				Main.getController().changeBottom(null);
				Main.getController().changeLeft(true);
				Main.getController().changeCenter(new SettingsPane());
			}
		});
		settingsBox.getChildren().add(settings);

		Button logout = new Button("Logout");
		logout.setPrefSize(BUTTONWIDTH, BUTTONHEIGHT);
		logout.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				logout(false);
			}
		});
		settingsBox.getChildren().add(logout);

		Label userLabel = new Label("Logged in as: " + Main.getCurrentUser().getNameFull());
		userLabel.setTextFill(Color.WHITE);
		settingsBox.getChildren().add(userLabel);
		this.setCenter(buttonBox);
		this.setBottom(settingsBox);
		this.setBackground(Main.getMenuBackground());

	}

	public static void logout(boolean stayLoggedIn) {

		if (!stayLoggedIn)
			Main.getCurrentUser().setStayLoggedIn(false);
		Main.getCurrentUser().setLoggedIn(false);
		IO.saveCurrent(Main.getCurrentUser());

		Main.getController().changeLeft(null);
		Main.getController().changeTop(null);
		Main.getController().changeBottom(null);
		Main.getController().changeCenter(new Login());
	}

}
