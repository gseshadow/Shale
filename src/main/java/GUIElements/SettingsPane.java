package GUIElements;

import java.awt.Desktop;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Scanner;
import application.Main;
import connections.ConnectionResources;
import connections.Server;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.image.Image;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import tools.PotentiaListGenerator;

public class SettingsPane extends FlowPane {

	public SettingsPane() {
		this.setHgap(10);
		this.setVgap(10);
		this.setPadding(new Insets(10));
		this.setAlignment(Pos.CENTER);
		setupListBox();
		setupBackgroundBox();
		setupProfileBox();
		setupUpdateBox();
		setupAboutBox();
		setupLogBox();
	}

	private void setupListBox() {
		VBox listBox = new VBox();
		listBox.setPadding(new Insets(10));
		listBox.setSpacing(10);
		listBox.setAlignment(Pos.CENTER);
		Label listLabel = new Label("Generate Excel document for current potentials:");
		listLabel.setStyle("-fx-font-weight: bold");
		Label messageLabel = new Label();
		Button list = new Button("Generate List");
		list.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {

				FileChooser fc = new FileChooser();
				fc.setInitialFileName("Index of Potentials - " + LocalDate.now().toString() + ".xlsx");
				fc.setTitle("Generate Intake List");

				/**
				 * Check for existing custom intake file location else default to user documents
				 */
				String intakeListFolder = "";
				try {
					File intakeListCustomLocation = new File(Main.getDefaultLocation() + System.getProperty("file.separator") + "Resources"
							+ System.getProperty("file.separator") + "IntakeListSaveLoc.txt");
					if (intakeListCustomLocation.exists()) {
						/*
						 * there is a file in resources telling new location for intakes set file location to that
						 * URL
						 */
						Scanner scanner;

						scanner = new Scanner(intakeListCustomLocation);

						intakeListFolder = scanner.nextLine();

						scanner.close();
					} else {
						FileWriter fw = new FileWriter(intakeListCustomLocation);
						fw.write(System.getProperty("user.home") + System.getProperty("file.separator") + "Documents");
						fw.close();
						intakeListFolder = System.getProperty("user.home") + System.getProperty("file.separator") + "Documents";
					}
				} catch (IOException e) {
					e.printStackTrace();
				}

				fc.setInitialDirectory(new File(intakeListFolder));
				fc.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Excel Document", "*.xlsx*"));
				File f = fc.showSaveDialog(Window.getWindows().get(0));
				if (f != null) {
					try {
						FileWriter fw = new FileWriter(Main.getDefaultLocation() + System.getProperty("file.separator") + "Resources"
								+ System.getProperty("file.separator") + "IntakeListSaveLoc.txt");
						fw.write(f.getParent());
						fw.close();
					} catch (IOException e1) {
						e1.printStackTrace();
					}

					messageLabel.setText(new PotentiaListGenerator(new PotentialsBubbles().getPotentials(), f).getMessage());
					try {
						Desktop.getDesktop().open(f.getParentFile());
					} catch (IOException e) {
						e.printStackTrace();
					}
				}

			}
		});
		listBox.getChildren().add(listLabel);
		listBox.getChildren().add(messageLabel);
		listBox.getChildren().add(list);

		setupBorder(listBox);
		setupBackground(listBox, Color.LIGHTBLUE);

		this.getChildren().add(listBox);
	}

	private void setupBackgroundBox() {
		VBox backBox = new VBox();
		backBox.setPadding(new Insets(10));
		backBox.setSpacing(10);
		backBox.setAlignment(Pos.CENTER);
		Label backLabel = new Label("Change Background Image");
		backLabel.setStyle("-fx-font-weight: bold");
		Label messageLabel = new Label();

		Button defaultBackground = new Button("Default Image");
		defaultBackground.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				try {
					try {
						File backgroundCustomLocation = new File(Main.getDefaultLocation() + System.getProperty("file.separator") + "Resources"
								+ System.getProperty("file.separator") + "CustomBackgroundLocation.txt");
                                                FileWriter fw = new FileWriter(backgroundCustomLocation);
                                                fw.write(Main.getDefaultBackgroundLocation());
                                                fw.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
					Image i = new Image(new FileInputStream(new File(Main.getDefaultBackgroundLocation())));
					Main.setBackgroundImage(i);
					Main.getController().changeBackground(i);

				} catch (FileNotFoundException e) {
					e.printStackTrace();
				}

			}
		});

		Button backgroundButton = new Button("Select Image");
		backgroundButton.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				FileChooser fc = new FileChooser();
				fc.setTitle("Choose Background Image");

				/**
				 * Check for existing custom background file location else default to user photos
				 */
				String backgroundFolder = "";
				File backgroundImageFile = null;
				File backgroundCustomLocation = new File(Main.getDefaultLocation() + System.getProperty("file.separator") + "Resources"
						+ System.getProperty("file.separator") + "CustomBackgroundLocation.txt");

				try {
					if (backgroundCustomLocation.exists()) {
						/*
						 * there is a file in resources telling new location for background set file location to
						 * that URL
						 */
						Scanner scanner;

						scanner = new Scanner(backgroundCustomLocation);

						backgroundImageFile = new File(scanner.nextLine());
						backgroundFolder = backgroundImageFile.getParent().toString();

						scanner.close();
					} else {
                                                FileWriter fw = new FileWriter(backgroundCustomLocation);
                                                fw.write(Main.getDefaultBackgroundLocation());
                                                fw.close();
						backgroundFolder = System.getProperty("user.home") + System.getProperty("file.separator") + "Pictures";
					}
				} catch (IOException e) {
					e.printStackTrace();
				}

				/**
				 * Save background file location and change background
				 */
				fc.setInitialDirectory(new File(backgroundFolder));
				fc.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("All", "*.*"), new FileChooser.ExtensionFilter("PNG", "*.png*"),
						new FileChooser.ExtensionFilter("Jpeg", "*.jpg*"));
				File f = fc.showOpenDialog(Window.getWindows().get(0));
				if (f != null) {

					try {
						FileWriter fw;
						fw = new FileWriter(backgroundCustomLocation);
						fw.write(f.getAbsolutePath());
						fw.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
					Image i = new Image(f.toURI().toString());
					Main.setBackgroundImage(i);
					Main.getController().changeBackground(i);
				}
			}
		});
		backBox.getChildren().add(backLabel);
		backBox.getChildren().add(messageLabel);

		HBox buttonBox = new HBox();
		buttonBox.setSpacing(5);
		buttonBox.setAlignment(Pos.CENTER);
		buttonBox.getChildren().add(backgroundButton);
		buttonBox.getChildren().add(defaultBackground);
		backBox.getChildren().add(buttonBox);

		setupBorder(backBox);
		setupBackground(backBox, Color.LIGHTBLUE);

		this.getChildren().add(backBox);

	}

	private void setupProfileBox() {
		VBox profileBox = new VBox();
		profileBox.setPadding(new Insets(10));
		profileBox.setSpacing(10);
		profileBox.setAlignment(Pos.CENTER);
		Label profileLabel = new Label("Access / Edit Your Profile");
		profileLabel.setStyle("-fx-font-weight: bold");
		Label messageLabel = new Label();
		Button profile = new Button("View My Profile");
		profile.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				Main.getController().changeCenter(new ProfileView(Main.getCurrentUser()));
				Main.getController().changeBottom(null);
				Main.getController().changeTop(null);
				Main.getController().changeLeft(true);
			}
		});
		profileBox.getChildren().add(profileLabel);
		profileBox.getChildren().add(messageLabel);
		profileBox.getChildren().add(profile);

		setupBorder(profileBox);
		setupBackground(profileBox, Color.LIGHTBLUE);
		this.getChildren().add(profileBox);
	}

	private void setupUpdateBox() {
		VBox box = new VBox();
		box.setPadding(new Insets(10));
		box.setSpacing(10);
		box.setAlignment(Pos.CENTER);
		Label label = new Label("Click Here to Check For Updates");
		label.setStyle("-fx-font-weight: bold");
		Label messageLabel = new Label();
		Button update = new Button("Check for Updates");
		update.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {

				Task<Integer> t = new Task<Integer>() {

					@Override
					protected Integer call() throws Exception {

						/*
						 * 
						 */
						try {

							double serverVersion = Server.getUpdate(Main.getVersion(), ConnectionResources.getConnection());
							if (Main.isGlobalDebug()) {
								System.out.println();
								System.out.println("Server Version: " + serverVersion);
								System.out.println("Local Version: " + Main.getVersion());
							}
							if (serverVersion > Main.getVersion()) {
								Alert alert = new Alert(AlertType.CONFIRMATION);
								alert.setTitle("Update Available");
								alert.setHeaderText("An update is available. Would you like to install it now?");
								alert.setContentText("Selecting OK will close the application and install updates.");
								Optional<ButtonType> result = alert.showAndWait();
								if (result.isPresent() && result.get() == ButtonType.OK) {
									if (Main.isGlobalDebug()) {
										if (Main.isGlobalDebug())
											System.out.println("-- Starting Update --");
										Process process;
										try {
											process = new ProcessBuilder(
													System.getProperty("user.home") + System.getProperty("file.separator") + "AppData"
															+ System.getProperty("file.separator") + "Local" + System.getProperty("file.separator")
															+ "Shale" + System.getProperty("file.separator") + "ShaleUp.exe")
													.start();// TODO
											// make
											// system
											// specific
											if (Main.isGlobalDebug())
												System.out.println(process.info());

											Platform.exit();
											System.exit(0);
										} catch (IOException e) {
											e.printStackTrace();
											System.exit(0);
										}
									}

								}
							} else {
								if (Main.isGlobalDebug()) {
									Alert alert = new Alert(AlertType.INFORMATION);
									alert.setTitle("Checking for Updates...");
									alert.setHeaderText("No update availabel at this time.");
									alert.showAndWait();
								}
							}
						} catch (Exception e) {
							System.out.println(e.getMessage());
						}
						/*
						 * 
						 */
						return null;
					}
				};

				Platform.runLater(t);
			}
		});
		box.getChildren().add(label);
		box.getChildren().add(messageLabel);
		box.getChildren().add(update);

		setupBorder(box);
		setupBackground(box, Color.LIGHTBLUE);

		this.getChildren().add(box);
	}

	private void setupAboutBox() {
		VBox aboutBox = new VBox();
		aboutBox.setPadding(new Insets(10));
		aboutBox.setSpacing(10);
		aboutBox.setAlignment(Pos.CENTER);
		Label aboutLabel = new Label("About");
		aboutLabel.setStyle("-fx-font-weight: bold");

		Label versionLabel = new Label("Version: " + Main.getVersion());

		aboutBox.getChildren().add(aboutLabel);
		aboutBox.getChildren().add(versionLabel);

		setupBorder(aboutBox);
		setupBackground(aboutBox, Color.GRAY);

		Button terminal = new Button("Terminal");
		terminal.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent event) {
				Main.showTerminal();

			}
		});
		aboutBox.getChildren().add(terminal);

		Button forceUpdate = new Button("Force Update");
		forceUpdate.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent event) {
				Process process;
				try {
					process = new ProcessBuilder(
							System.getProperty("user.home") + System.getProperty("file.separator") + "AppData"
									+ System.getProperty("file.separator") + "Local"
									+ System.getProperty("file.separator") + "Shale"
									+ System.getProperty("file.separator") + "ShaleUp.exe")
							.start();// TODO
					// make
					// system
					// specific
					if (Main.isGlobalDebug())
						System.out.println(process.info());

					try {
						File temp = new File(Main.getDefaultBackgroundLocation() + System.getProperty("file.separator") + "Resources"
								+ System.getProperty("file.separator") + "lock.lk");
						if (temp.exists()) {
							temp.delete();
						}

						Platform.exit();
						System.exit(0);
					} catch (Exception e) {
						e.printStackTrace();
					}
				} catch (IOException e) {
					e.printStackTrace();
					System.exit(0);
				}

			}
		});
		aboutBox.getChildren().add(forceUpdate);

		this.getChildren().add(aboutBox);
	}

	private void setupBorder(VBox box) {
		box.setBorder(new Border(new BorderStroke(Color.BLACK, BorderStrokeStyle.SOLID, new CornerRadii(2), new BorderWidths(3))));

	}

	private void setupBackground(VBox box, Color color) {
		box.setBackground(new Background(new BackgroundFill(color, new CornerRadii(2), new Insets(1))));

	}

	private void setupLogBox() {
		VBox logBox = new VBox();
		logBox.setPadding(new Insets(10));
		logBox.setSpacing(10);
		logBox.setAlignment(Pos.CENTER);
		Label listLabel = new Label("Audit Log");
		listLabel.setStyle("-fx-font-weight: bold");
		Label messageLabel = new Label();
		Button showLogs = new Button("Show Logs");
		showLogs.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				Main.getController().changeCenter(new LogViewer());
			}
		});
		logBox.getChildren().add(listLabel);
		logBox.getChildren().add(messageLabel);
		logBox.getChildren().add(showLogs);

		setupBorder(logBox);
		setupBackground(logBox, Color.LIGHTBLUE);

		this.getChildren().add(logBox);
	}
}
