package application;

import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Collections;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
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

public class PotentialsList extends FlowPane {
	private ArrayList<Potential> potentials = new ArrayList<>();
	private ArrayList<Potential> accepted = new ArrayList<>();
	private ArrayList<Potential> rejected = new ArrayList<>();

	File potentialsFile = new File(Main.getFileLocation() + "\\Potentials");
	File[] potentialsFiles = potentialsFile.listFiles();

	File acceptedFile = new File(Main.getFileLocation() + "\\Accepted");
	File[] acceptedFiles = acceptedFile.listFiles();

	File rejectedFile = new File(Main.getFileLocation() + "\\Rejected");
	File[] rejectedFiles = rejectedFile.listFiles();

	public PotentialsList() {

		this.setHgap(10);
		this.setVgap(10);
		this.setPadding(new Insets(5));
		this.setAlignment(Pos.TOP_LEFT);

		for (File f : potentialsFiles) {
			potentials.add((Potential) readPotential(f));
		}

		for (File f : acceptedFiles) {
			accepted.add((Potential) readPotential(f));
		}

		for (File f : rejectedFiles) {
			rejected.add((Potential) readPotential(f));
		}

		Collections.sort(potentials, new PotentialComarator());
		Collections.sort(accepted, new PotentialComarator());
		Collections.sort(rejected, new PotentialComarator());

		setupList("", true, true, true);

	}

	private void setupList(String searchCriteria, boolean po, boolean ac, boolean re) {

		if (po)
			this.getChildren().add(getIntakeFlowPane(potentials, "Potentials", Color.ORANGE, searchCriteria));
		if (ac)
			this.getChildren().add(getIntakeFlowPane(accepted, "Accepted", Color.GREEN, searchCriteria));
		if (re)
			this.getChildren().add(getIntakeFlowPane(rejected, "Rejected", Color.RED, searchCriteria));

	}

	private Object readPotential(File file) {

		Object result = null;
		try {
			FileInputStream fis = new FileInputStream(file);
			ObjectInputStream ois = new ObjectInputStream(fis);
			result = ois.readObject();
			ois.close();
		} catch (Exception e) {
			System.out.println("Error: PotentialsList.readPotential()");
		}
		return result;
	}

	private VBox getIntakeFlowPane(ArrayList<Potential> list, String header, Color borderColor, String searchCriteria) {
		VBox box = new VBox();
		box.prefWidthProperty().bind(this.widthProperty().subtract(20));
		box.setSpacing(10);
		box.setAlignment(Pos.TOP_LEFT);
		box.setPadding(new Insets(10));
		box.setBorder(new Border(
				new BorderStroke(borderColor, BorderStrokeStyle.SOLID, new CornerRadii(2), new BorderWidths(2))));
		Label head = new Label(header);
		head.setUnderline(true);
		head.setStyle("-fx-font-size: 24; -font-weight: bold");
		box.getChildren().add(head);

		FlowPane fp = new FlowPane();
		fp.setHgap(10);
		fp.setVgap(10);
		fp.setPadding(new Insets(5));
		fp.setAlignment(Pos.TOP_LEFT);

		for (Potential p : list) {
			if (p.getClientNameFirst().toLowerCase().contains(searchCriteria)
					|| p.getClientNameLast().toLowerCase().contains(searchCriteria))
				fp.getChildren().add(getPBox(p));
		}
		box.getChildren().add(fp);

		return box;
	}

	private HBox getPBox(Potential p) {

		HBox clientBox = new HBox();
		clientBox.setSpacing(10);
		clientBox.setAlignment(Pos.CENTER_LEFT);
		clientBox.setPadding(new Insets(10));
		clientBox.setBackground(new Background(new BackgroundFill(Color.GRAY, new CornerRadii(5), new Insets(-1))));
		clientBox.setMinSize(400, 100);

		clientBox.setOnMouseEntered(new EventHandler<Event>() {

			@Override
			public void handle(Event arg0) {
				setCursor(Cursor.HAND);
			}
		});

		clientBox.setOnMouseExited(new EventHandler<Event>() {

			@Override
			public void handle(Event arg0) {
				setCursor(Cursor.DEFAULT);
			}
		});

		clientBox.setOnMouseClicked(new EventHandler<Event>() {

			@Override
			public void handle(Event arg0) {
				Intake temp = new Intake(p);
				Main.setPotential(p);
				Main.setIntake(temp);
				Main.getController().changeCenter(temp);
				int mode = 0;
				if (p.isAccepted()) {
					mode = 3;
				} else if (p.isRejected()) {
					mode = 4;
				} else {
					mode = 1;
				}
				Main.getController().changeTop(new TopBar(mode, temp));
				Main.getController().changeBottom(null);
				Main.getController().changeLeft(true);
			}
		});

		Label title = new Label(p.getClientNameLast() + ", " + p.getClientNameFirst());
		title.setStyle("-fx-font-size: 24; -font-weight: bold");

		clientBox.getChildren().add(title);

		return clientBox;
	}

	public void searching(String searchCriteria, boolean potential, boolean accepted, boolean rejected) {

		System.out.println("Searching: " + searchCriteria);// TODO
		this.getChildren().clear();
		setupList(searchCriteria.toLowerCase(), potential, accepted, rejected);
	}

}
