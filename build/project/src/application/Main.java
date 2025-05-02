package application;

import java.io.File;

import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;

public class Main extends Application {

	private static Potential currentPotential;
	private static Intake currentIntake;
	private static String fileLocation;
	private static InputPaneController controller;

	@Override
	public void start(Stage primaryStage) {

		fileLocation = System.getProperty("user.home") + "\\AppData\\Local\\IntakeGenerator";
		File file = new File(fileLocation);
		try {
			if (file.mkdir()) {
				System.out.println("Directory Created");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		file = new File(System.getProperty("user.home") + "\\AppData\\Local\\IntakeGenerator\\Potentials");
		try {
			if (file.mkdir()) {
				System.out.println("Directory Created");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		file = new File(System.getProperty("user.home") + "\\AppData\\Local\\IntakeGenerator\\Rejected");
		try {
			if (file.mkdir()) {
				System.out.println("Directory Created");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		file = new File(System.getProperty("user.home") + "\\AppData\\Local\\IntakeGenerator\\Accepted");
		try {
			if (file.mkdir()) {
				System.out.println("Directory Created");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		file = new File(System.getProperty("user.home") + "\\AppData\\Local\\IntakeGenerator\\Deleted");
		try {
			if (file.mkdir()) {
				System.out.println("Directory Created");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		file = new File(System.getProperty("user.home") + "\\AppData\\Local\\IntakeGenerator\\IntakeResources");
		try {
			if (file.mkdir()) {
				System.out.println("Directory Created");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		try {
			BorderPane root = (BorderPane) FXMLLoader.load(getClass().getResource("InputPane.fxml"));
			Scene scene = new Scene(root, 1600, 900);
			scene.getStylesheets().add(getClass().getResource("application.css").toExternalForm());
			primaryStage.setScene(scene);
			primaryStage.show();
			primaryStage.setTitle("Intake Manager");
			primaryStage.setMinWidth(700);
			primaryStage.setMinHeight(500);

		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	public static void main(String[] args) {
		launch(args);
	}

	public static void setIntake(Intake in) {
		currentIntake = in;
	}

	public static Intake getCurrentIntake() {
		return currentIntake;
	}

	public static void setPotential(Potential po) {
		currentPotential = po;
	}

	public static Potential getCurrentPotential() {
		return currentPotential;
	}

	public static String getFileLocation() {
		return fileLocation;
	}

	public static void setController(InputPaneController inputPaneController) {
		controller = inputPaneController;

	}

	public static InputPaneController getController() {
		return controller;
	}

}
