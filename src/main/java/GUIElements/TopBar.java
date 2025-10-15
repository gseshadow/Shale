package GUIElements;

import java.awt.Desktop;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Scanner;

import application.Main;
import connections.ConnectionResources;
import connections.Server;
import dataStructures.Case;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import tools.IntakeTemplateGenerator;

public class TopBar extends HBox {

	private Button save = new Button("Save");
	private Button cancel = new Button("Cancel");
//	private Button accept = new Button("Accept");
	private Button transfer = new Button("Transfer Case");
	private Button denied = new Button("Deny");
	private Button delete = new Button("Delete");
	private Button close = new Button("Close");
	private Button potential = new Button("Potential");
	private Button intakeDoc = new Button("Create Intake Document");
	private Case cse;
	private ArrayList<Button> buttons = new ArrayList<>();
	private boolean debug = Main.isGlobalDebug();

	public TopBar(int mode, IntakePane intake) {
		cse = intake.getCase();

		if (cse.getCaseOrganizationId() == 0) {
			cse.setCaseOrganizationId(Main.getCurrentUser().getDefault_organization());
		}
		this.setSpacing(10);
		this.setPadding(new Insets(10));
		this.setAlignment(Pos.TOP_RIGHT);
		this.setBackground(Main.getMenuBackground());

		if (mode == 0) {// New Case
//			buttons.add(accept);
//			buttons.add(denied);
			buttons.add(cancel);
			buttons.add(save);
//			buttons.add(close);
			buttons.add(intakeDoc);
		} else if (mode == 9) {// Existing Case Potential
//			buttons.add(accept);
			buttons.add(denied);
			buttons.add(transfer);
//			buttons.add(cancel);
//			buttons.add(save);
			buttons.add(delete);
//			buttons.add(close);
			buttons.add(intakeDoc);
		} else if (mode == 10) {// Existing Case Closed
			buttons.add(potential);
//			buttons.add(accept);
			buttons.add(denied);
			buttons.add(transfer);
//			buttons.add(cancel);
//			buttons.add(save);
			buttons.add(delete);
//			buttons.add(close);
			buttons.add(intakeDoc);
		} else if (mode == 13) {// Existing Case Accepted
			buttons.add(potential);
			buttons.add(denied);
			buttons.add(transfer);
//			buttons.add(cancel);
//			buttons.add(save);
			buttons.add(delete);
			buttons.add(close);
			buttons.add(intakeDoc);
		} else if (mode == 14) {// Existing Case Denied
			buttons.add(potential);
//			buttons.add(accept);
//			buttons.add(cancel);
//			buttons.add(save);
			buttons.add(delete);
			buttons.add(close);
			buttons.add(intakeDoc);
		} else if (mode == 17) {// Existing Case Active (Transferred)
			buttons.add(potential);
//			buttons.add(accept);
//			buttons.add(cancel);
//			buttons.add(save);
			buttons.add(delete);
			buttons.add(close);
			buttons.add(intakeDoc);

		}

		for (Button b : buttons) {
			b.setPrefSize(100, 25);
		}
		intakeDoc.setPrefSize(150, 25);

		this.getChildren().addAll(buttons);

		setupButton(save, Color.LIGHTGREEN);
		save.setOnAction(new EventHandler<ActionEvent>() {// TODO rework this thing

			@Override
			public void handle(ActionEvent arg0) {

				try {
					String filepath = Main.getDefaultLocation() + System.getProperty("file.separator") + "LocalBackup"
							+ System.getProperty("file.separator") + intake.getCase().getCaseName();
					FileOutputStream fileOut = new FileOutputStream(filepath);
					ObjectOutputStream objectOut = new ObjectOutputStream(fileOut);
					objectOut.writeObject(intake.getCase());
					objectOut.close();

					Main.getController().changeTop(new TopBar(9, intake));
					Main.getController().changeLeft(new MenuList(true, false));

				} catch (Exception ex) {
					try {
						Files.write(Paths.get(Main.getLog().getAbsolutePath()), ex.getMessage().getBytes(), StandardOpenOption.APPEND);
					} catch (IOException e1) {
						e1.printStackTrace();
					}
					ex.printStackTrace();
				}
//				Main.getCases().put(cse.get_id(), cse);

			}
		});

		setupButton(delete, Color.RED);
		delete.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {

				if (debug)
					System.out.println(intake.getCase().get_id() + " Delete Button Pushed ID");// TODO remove
				Main.getController().changeCenter(new PopupConfirm("Please confirm that you would like to DELETE this intake", intake, true));

			}
		});

		setupButton(cancel, Color.GRAY);
		cancel.setOnAction(new EventHandler<ActionEvent>() { // TODO remove all previously created content

			@Override
			public void handle(ActionEvent arg0) {
				Case temp = Main.getCases().get(Main.getCurrentCaseId());

				Main.getCases().remove(Main.getCurrentCaseId());

				Main.getIncidents().remove(intake.getCase().getIncidentId());

				Task<Integer> t = new Task<Integer>() {

					@Override
					protected Integer call() throws Exception {
						int caller = temp.getCallerId();
						int client = temp.getClientId();

						Server.deleteCase(temp.get_id(), ConnectionResources.getConnection());
						Server.deleteContact(caller, ConnectionResources.getConnection());
						Server.deleteContact(client, ConnectionResources.getConnection());
						return null;
					}
				};
				new Thread(t).start();

				Main.getController().changeCenter(new PotentialsBubbles());
				Main.getController().changeTop(null);
				Main.getController().changeLeft(false);

			}
		});

//		setupButton(accept, Color.TURQUOISE);
//		accept.setOnAction(new EventHandler<ActionEvent>() {
//
//			@Override
//			public void handle(ActionEvent arg0) {
//				cse = intake.getCase();
//
//				cse.setPotential(false);
//				cse.setAccepted(true);
//				cse.setTransferred(false);
//				cse.setDenied(false);
//				cse.setClosed(false);
//
//				cse.setCaseStatusString("Accepted");
//				Task<Integer> t = new Task<Integer>() {
//
//					@Override
//					protected Integer call() throws Exception {
//						Server.updateCaseIntField(cse.get_id(), "caseStatusId", 13, ConnectionResources.getConnection());
//						return null;
//					}
//				};
//				new Thread(t).start();
//
//				Main.getCases().get(intake.getCase().get_id()).setCaseStatusId(13);
//				Main.getController().changeCenter(new IntakePane(Main.getCurrentCase(), false));
//				Main.getController().changeTop(new TopBar(13, intake));
//
//			}
//		});

		setupButton(denied, Color.DARKORANGE);
		denied.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {

				cse = intake.getCase();
				cse.setPotential(false);
				cse.setAccepted(false);
				cse.setTransferred(false);
				cse.setDenied(true);
				cse.setClosed(false);

				cse.setCaseStatusString("Denied");
				Task<Integer> t = new Task<Integer>() {

					@Override
					protected Integer call() throws Exception {
						Server.updateCaseIntField(cse.get_id(), "caseStatusId", 14, ConnectionResources.getConnection());
						return null;
					}
				};
				new Thread(t).start();

				Main.getCases().get(intake.getCase().get_id()).setCaseStatusId(14);
				Main.getController().changeCenter(new IntakePane(Main.getCurrentCase(), false));
				Main.getController().changeTop(new TopBar(14, intake));
			}
		});

		setupButton(transfer, Color.LIMEGREEN);
		transfer.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				boolean confirm = false;
				cse = intake.getCase();

				if (!cse.isFeeAgreementSigned()) {
					Alert notSigned = new Alert(AlertType.CONFIRMATION);
					((Stage) notSigned.getDialogPane().getScene().getWindow()).getIcons().add(Main.getLogo());
					notSigned.setTitle("Mark Case as Transferred");
					notSigned.setHeaderText("Transfer Case?");
					notSigned.setContentText("This case does not have a signed Fee Agreement. \nTransfer anyway?");
					Optional<ButtonType> result = notSigned.showAndWait();
					if (result.isPresent() && result.get() == ButtonType.OK) {

						confirm = true;

					}
				} else
					confirm = true;

				if (confirm) {
					cse.setPotential(false);
					cse.setAccepted(true);
					cse.setTransferred(true);
					cse.setDenied(false);
					cse.setClosed(false);
					cse.setCaseStatusString("Active");
					Task<Integer> t = new Task<Integer>() {

						@Override
						protected Integer call() throws Exception {
							Server.updateCaseIntField(cse.get_id(), "caseStatusId", 17, ConnectionResources.getConnection());
							return null;
						}
					};
					new Thread(t).start();

					Main.getCases().get(intake.getCase().get_id()).setCaseStatusId(17);
					Main.getController().changeCenter(new IntakePane(Main.getCurrentCase(), false));
					Main.getController().changeTop(new TopBar(17, intake));
				}
			}
		});

		setupButton(potential, Color.GREENYELLOW);
		potential.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				cse = intake.getCase();
				cse.setPotential(true);
				cse.setAccepted(false);
				cse.setTransferred(false);
				cse.setDenied(false);
				cse.setClosed(false);
				cse.setCaseStatusString("Potential");
				Task<Integer> t = new Task<Integer>() {

					@Override
					protected Integer call() throws Exception {
						Server.updateCaseIntField(cse.get_id(), "caseStatusId", 9, ConnectionResources.getConnection());
						return null;
					}
				};
				new Thread(t).start();
				Main.getCases().get(intake.getCase().get_id()).setCaseStatusId(9);

				Main.getController().changeCenter(new IntakePane(Main.getCurrentCase(), false));
				Main.getController().changeTop(new TopBar(9, intake));
			}
		});

		setupButton(close, Color.DARKGRAY);
		close.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				cse = intake.getCase();
//				cse.setAccepted(false);
//				cse.setDenied(false);
//				cse.setTransferred(false);
				cse.setClosed(true);
				cse.setClosedDate(LocalDate.now());
				cse.setCaseStatusString("Closed");
				Task<Integer> t = new Task<Integer>() {

					@Override
					protected Integer call() throws Exception {

						Server.updateCaseIntField(cse.get_id(), "caseStatusId", 10, ConnectionResources.getConnection());
						return null;
					}
				};
				new Thread(t).start();

				Main.getCases().get(intake.getCase().get_id()).setCaseStatusId(10);
				Main.getController().changeCenter(new IntakePane(Main.getCurrentCase(), false));
				Main.getController().changeTop(new TopBar(10, intake));

			}
		});

		setupButton(intakeDoc, Color.LIGHTSEAGREEN);
		intakeDoc.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {

				FileChooser fc = new FileChooser();
				if (intake.getCase().isClientEstate()) {
					fc.setInitialFileName(
							intake.getCase().getClientNameLast() + ", " + intake.getCase().getClientNameFirst() + " Estate of" + " - Intake.docx");
				} else
					fc.setInitialFileName(intake.getCase().getClientNameLast() + ", " + intake.getCase().getClientNameFirst() + " - Intake.docx");
				fc.setTitle("Generate Intake Document");
				/**
				 * Check for existing custom intake file location else default to user documents
				 */
				String intakeFolder = "";
				try {
					File intakeCustomLocation = new File(Main.getDefaultLocation() + System.getProperty("file.separator") + "Resources"
							+ System.getProperty("file.separator") + "IntakeSaveLoc.txt");
					if (intakeCustomLocation.exists()) {
						/*
						 * there is a file in resources telling new location for intakes set file location to that
						 * URL
						 */
						Scanner scanner;

						scanner = new Scanner(intakeCustomLocation);
						intakeFolder = scanner.nextLine();
						scanner.close();
					} else {
						FileWriter fw = new FileWriter(intakeCustomLocation);
						fw.write(System.getProperty("user.home") + System.getProperty("file.separator") + "Documents");
						fw.close();
						intakeFolder = System.getProperty("user.home") + System.getProperty("file.separator") + "Documents";
					}

				} catch (IOException e) {

					try {
						Files.write(Paths.get(Main.getLog().getAbsolutePath()), e.getMessage().getBytes(), StandardOpenOption.APPEND);
					} catch (IOException e1) {
						e1.printStackTrace();
					}
					e.printStackTrace();
				}

				/**
				 * Generate and save intake document to specified file location.
				 */
				fc.setInitialDirectory(new File(intakeFolder));
				fc.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Word Document", "*.docx*"));
				File f = fc.showSaveDialog(/* Window.getWindows().get(0) */null);// TODO

				if (f != null) {
					{
						try {
							FileWriter fw = new FileWriter(Main.getDefaultLocation() + System.getProperty("file.separator") + "Resources"
									+ System.getProperty("file.separator") + "IntakeSaveLoc.txt");
							fw.write(f.getParent());
							fw.close();
						} catch (IOException e1) {
							try {
								Files.write(Paths.get(Main.getLog().getAbsolutePath()), e1.getMessage().getBytes(), StandardOpenOption.APPEND);
							} catch (IOException e) {
								e.printStackTrace();
							}
							e1.printStackTrace();
						}

						Task<Integer> t = new Task<Integer>() {

							@Override
							protected Integer call() throws Exception {
								try {
									new IntakeTemplateGenerator(intake.getCase(), f);
								} catch (Exception e) {
									if (Main.isGlobalDebug())
										try {
											Files.write(Paths.get(Main.getLog().getAbsolutePath()), e.getMessage().getBytes(), StandardOpenOption.APPEND);
										} catch (IOException e1) {
											e1.printStackTrace();
										}
									e.printStackTrace();
								}

								Desktop.getDesktop().open(f.getParentFile());

								return 0;
							}
						};
						try {
							new Thread(t).start();
						} catch (Exception e) {
							try {
								Files.write(Paths.get(Main.getLog().getAbsolutePath()), e.getMessage().getBytes(), StandardOpenOption.APPEND);
							} catch (IOException e1) {
								e1.printStackTrace();
							}
							e.printStackTrace();
						}
					}
				} else {
				}

			}
		});

	}

	private void setupButton(Button button, Color color) {
		button.setBackground(new Background(new BackgroundFill(color, new CornerRadii(2), new Insets(1))));
		button.setBorder(new Border(new BorderStroke(Color.BLACK, BorderStrokeStyle.SOLID, new CornerRadii(2), new BorderWidths(1), new Insets(1))));
		button.setOnMouseEntered(new EventHandler<Event>() {

			@Override
			public void handle(Event arg0) {
				button.setCursor(Cursor.HAND);

			}
		});
		button.setOnMouseExited(new EventHandler<Event>() {

			@Override
			public void handle(Event arg0) {
				button.setCursor(Cursor.DEFAULT);

			}
		});
	}

}
