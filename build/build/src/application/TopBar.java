package application;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;

public class TopBar extends HBox {

	private Button save = new Button("Save");
	private Button cancel = new Button("Cancel");
	private Button accept = new Button("Accept");
	private Button reject = new Button("Reject");
	private Button delete = new Button("Delete");
	private Button potential = new Button("Potential");
	private Button intakeDoc = new Button("Create Intake Document");

	private ArrayList<Button> buttons = new ArrayList<>();

	public TopBar(int mode, Intake intake) {
		this.setSpacing(10);
		this.setPadding(new Insets(10));
		this.setAlignment(Pos.TOP_RIGHT);

		if (mode == 0) {// New Case
			buttons.add(accept);
			buttons.add(reject);
			buttons.add(cancel);
			buttons.add(save);
			buttons.add(intakeDoc);
		} else if (mode == 1) {// Existing Case Potential
			buttons.add(accept);
			buttons.add(reject);
			buttons.add(cancel);
			buttons.add(save);
			buttons.add(delete);
			buttons.add(intakeDoc);
		} else if (mode == 3) {// Existing Case Accepted
			buttons.add(potential);
			buttons.add(reject);
			buttons.add(cancel);
			buttons.add(save);
			buttons.add(delete);
			buttons.add(intakeDoc);
		} else if (mode == 4) {// Existing Case Rejected
			buttons.add(potential);
			buttons.add(accept);
			buttons.add(cancel);
			buttons.add(save);
			buttons.add(delete);
			buttons.add(intakeDoc);
		}

		for (Button b : buttons) {
			b.setPrefSize(100, 25);
		}
		intakeDoc.setPrefSize(150, 25);

		this.getChildren().addAll(buttons);

		save.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {

				if (intake.getPotential().getFileName().equals("")) {
					System.out.println("New Potential ----------");// TODO remove
					intake.setPrefix(Main.getFileLocation() + "\\Potentials\\");

					intake.setFileName(
							Main.getFileLocation() + "\\Potentials\\" + intake.getPotential().getClientNameLast() + ", "
									+ intake.getPotential().getClientNameFirst());
					Main.getController().changeTop(new TopBar(1, new Intake(intake.getPotential())));
				}
				String s = intake.getPotential().getPrefix() + "\\" + intake.getPotential().getClientNameLast() + ", "
						+ intake.getPotential().getClientNameFirst();
				if (!intake.getPotential().getFileName().equals(s)) {
					System.out.println("Name Change ----------");// TODO remove
					IO.saveCurrent(intake.getPotential(), s);
					Main.getController().changeCenter(new Intake(intake.getPotential()));

				} else {
					System.out.println("Update Potential----------");// TODO remove

					IO.saveCurrent(intake);

				}
			}

		});

		delete.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				{
					File remove = new File(intake.getPotential().getFileName());

					try {
						System.out.println(intake.getPotential().getFileName() + " : Delete");// TODO remove

						Files.move(
								Paths.get(intake.getPotential().getPrefix() + "\\"
										+ intake.getPotential().getClientNameLast() + ", "
										+ intake.getPotential().getClientNameFirst()),
								Paths.get(Main.getFileLocation() + "\\Deleted\\"
										+ intake.getPotential().getClientNameLast() + ", "
										+ intake.getPotential().getClientNameFirst()),
								StandardCopyOption.ATOMIC_MOVE);
					} catch (IOException e) {
						e.printStackTrace();
					}
					if (remove.delete()) {
						System.out.println("Deleted the file: " + remove.getName());
					} else {
						System.out.println("Failed to delete the file.");
					}
				}

				Main.getController().changeCenter(new PotentialsList());
				Main.getController().changeTop(null);
				Main.getController().changeLeft(false);

			}
		});

		cancel.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				Main.getController().changeCenter(new PotentialsList());
				Main.getController().changeTop(null);

			}
		});

		accept.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				{
					File remove = new File(intake.getPotential().getFileName());

					if (remove.delete()) {
						System.out.println("Deleted the file: " + remove.getName());
					} else {
						System.out.println("Failed to delete the file.");
					}
				}
				intake.setAccepted(true);
				intake.setPrefix(Main.getFileLocation() + "\\Accepted\\");

				intake.setFileName(Main.getFileLocation() + "\\Accepted\\" + intake.getPotential().getClientNameLast()
						+ ", " + intake.getPotential().getClientNameFirst());

				IO.saveCurrent(intake);
				Main.getController().changeCenter(new Intake(Main.getCurrentPotential()));
				Main.getController().changeTop(new TopBar(3, intake));

			}
		});

		reject.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				{
					File remove = new File(intake.getPotential().getFileName());

					if (remove.delete()) {
						System.out.println("Deleted the file: " + remove.getName());
					} else {
						System.out.println("Failed to delete the file.");
					}
				}
				intake.setRejected(true);
				intake.setPrefix(Main.getFileLocation() + "\\Rejected\\");

				intake.setFileName(Main.getFileLocation() + "\\Rejected\\" + intake.getPotential().getClientNameLast()
						+ ", " + intake.getPotential().getClientNameFirst());
				IO.saveCurrent(intake);
				Main.getController().changeCenter(new Intake(Main.getCurrentPotential()));
				Main.getController().changeTop(new TopBar(4, intake));
			}
		});
		potential.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				{
					File remove = new File(intake.getPotential().getFileName());

					if (remove.delete()) {
						System.out.println("Deleted the file: " + remove.getName());
					} else {
						System.out.println("Failed to delete the file.");
					}
				}

				intake.setRejected(false);
				intake.setAccepted(false);

				intake.setPrefix(Main.getFileLocation() + "\\Potentials\\");

				intake.setFileName(Main.getFileLocation() + "\\Potentials\\" + intake.getPotential().getClientNameLast()
						+ ", " + intake.getPotential().getClientNameFirst());
				IO.saveCurrent(intake);
				Main.getController().changeCenter(new Intake(Main.getCurrentPotential()));
				Main.getController().changeTop(new TopBar(1, intake));
			}
		});

		intakeDoc.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				FileChooser fc = new FileChooser();
				fc.setInitialFileName(intake.getPotential().getClientNameLast() + ", "
						+ intake.getPotential().getClientNameFirst() + " - Intake.docx");
				fc.setTitle("Generate Intake Document");
				fc.setInitialDirectory(new File(System.getProperty("user.home") + "\\Documents"));
				fc.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Word Document", "*.docx*"));

				File f = fc.showSaveDialog(null);
				if (f != null) {
					javafx.concurrent.Task<Integer> t = new Task<Integer>() {

						@Override
						protected Integer call() throws Exception {
							try {
								new IntakeTemplateGenerator(intake.getPotential(), f);
							} catch (Exception e) {
								System.out.println("BIG ASS FAILURE");
								e.printStackTrace();
							}
							return null;
						}
					};
					Thread thread = new Thread(t);
					thread.start();
				}

			}
		});

	}

}
