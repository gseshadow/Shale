package GUIElements;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import application.Main;
import dataStructures.Case;
import dataStructures.CaseBuilder;
import dataStructures.User;
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

public class ActiveCaseView extends FlowPane {
	private ArrayList<Case> transferred = new ArrayList<>();

	private Comparator<Case> caseNameSort = (Case o1, Case o2) -> o1.getCaseName().toLowerCase().compareTo(o2.getCaseName().toLowerCase());
	private Comparator<Case> dateSort = (Case o1, Case o2) -> o1.getCallerDate().toString().compareTo(o2.getCallerDate().toString());
	private Comparator<Case> solSort = new Comparator<Case>() {

		@Override
		public int compare(Case o1, Case o2) {
			if (o2.getIncidentStatuteOfLimitations() == null) {
				return 0;
			}
			if (o1.getIncidentStatuteOfLimitations() == null) {
				return 0;
			}

			return o1.getIncidentStatuteOfLimitations().toString().compareTo(o2.getIncidentStatuteOfLimitations().toString());

		}
	};
	private ArrayList<HBox> boxes = new ArrayList<>();

	private BorderWidths borderWidths = new BorderWidths(1);
	private Insets insets = new Insets(1);
	private CornerRadii cornerRadii = new CornerRadii(3);

	private int width = 250;
	private int height = 100;

	public ActiveCaseView() {

		this.setHgap(10);
		this.setVgap(10);
		this.setPadding(new Insets(5));
		this.setAlignment(Pos.TOP_LEFT);

		for (Case c : Main.getCases().values()) {
			int statusId = c.getCaseStatusId();
			if (statusId == 17) {
				transferred.add(c);
			}
		}
//		setupList("", true, true, true, true);

	}

	private void setupList(String searchCriteria, int sortMode, boolean myCases) {

		if (sortMode == 0) {
			Collections.sort(transferred, caseNameSort);
		} else if (sortMode == 1) {
			Collections.sort(transferred, dateSort);
		} else if (sortMode == 2) {
			Collections.sort(transferred, solSort);
		}

		this.getChildren().add(getIntakeFlowPane(transferred, "Active", Color.DARKGREEN, searchCriteria, myCases));
	}

	private VBox getIntakeFlowPane(ArrayList<Case> list, String header, Color borderColor, String searchCriteria, boolean myCases) {
		VBox box = new VBox();
		box.prefWidthProperty().bind(this.widthProperty().subtract(20));
		box.setSpacing(10);
		box.setAlignment(Pos.TOP_LEFT);
		box.setPadding(new Insets(10));
		box.setBorder(new Border(new BorderStroke(borderColor, BorderStrokeStyle.SOLID, cornerRadii, borderWidths)));

		Label head = new Label(header);
		head.setUnderline(true);
		head.setStyle("-fx-font-size: 24; -font-weight: bold");
		box.getChildren().add(head);

		FlowPane fp = new FlowPane();
		fp.setHgap(10);
		fp.setVgap(10);
		fp.setPadding(new Insets(5));
		fp.setAlignment(Pos.TOP_LEFT);

		for (Case p : list) {
			if (myCases) {
				if (p.getOfficeResponsibleAttorneyId() == Main.getCurrentUser().get_id()) {
					if (p != null) {
						String name = p.getCaseName();
						name = name.replace("Estate of", "");
						if (name.toLowerCase().contains(searchCriteria)) {
							fp.getChildren().add(getPBox(p));
						}
					}
				}
			} else if (p != null) {
				String name = p.getCaseName();
				name = name.replace("Estate of", "");
				if (name.toLowerCase().contains(searchCriteria)) {
					fp.getChildren().add(getPBox(p));
				}
			}

		}

		box.getChildren().add(fp);

		return box;
	}

	private HBox getPBox(Case p) {

		HBox clientBox = new HBox();
		clientBox.setSpacing(10);
		clientBox.setAlignment(Pos.CENTER_LEFT);
		clientBox.setPadding(new Insets(10));

		clientBox.setMinSize(100, 50);
		clientBox.setPrefSize(width * 10 * (Main.getCurrentUser().getIconSize() / 25), height * 10 * (Main.getCurrentUser().getIconSize() / 25));
		boxes.add(clientBox);

		/*
		 * Set Color based on practice area
		 */
//		int area = p.getCasePracticeAreaId();
//		if (area == 1) {
//			clientBox.setBackground(new Background(new BackgroundFill(medMalColor, cornerRadii, insets)));
//		} else if (area == 2) {
//			clientBox.setBackground(new Background(new BackgroundFill(pIColor, cornerRadii, insets)));
//		} else if (area == 3) {
//			clientBox.setBackground(new Background(new BackgroundFill(sAColor, cornerRadii, insets)));
//		} else {
//			clientBox.setBackground(new Background(new BackgroundFill(Color.GRAY, cornerRadii, insets)));
//		}
//		

		/*
		 * Set Color based on responsible attorney
		 */
		clientBox.setBackground(new Background(new BackgroundFill(Color.GRAY, cornerRadii, insets)));
		if (p.getOfficeResponsibleAttorneyId() != 0) {
			User attorney = Main.getUsers().get(p.getOfficeResponsibleAttorneyId());
			if (!attorney.getColor().equals("")) {
				clientBox.setBackground(new Background(new BackgroundFill(Color.valueOf(attorney.getColor()), cornerRadii, insets)));
			}
		}

		clientBox.setBorder(new Border(new BorderStroke(Color.BLACK, BorderStrokeStyle.SOLID, cornerRadii, borderWidths, insets)));
		clientBox.setOnMouseEntered(new EventHandler<Event>() {

			@Override
			public void handle(Event arg0) {
				clientBox.setCursor(Cursor.HAND);
				clientBox.setBorder(new Border(new BorderStroke(Color.RED, BorderStrokeStyle.SOLID, cornerRadii, borderWidths, insets)));

			}
		});

		clientBox.setOnMouseExited(new EventHandler<Event>() {

			@Override
			public void handle(Event arg0) {
				clientBox.setCursor(Cursor.DEFAULT);
				clientBox.setBorder(new Border(new BorderStroke(Color.BLACK, BorderStrokeStyle.SOLID, cornerRadii, borderWidths, insets)));

			}
		});
		clientBox.setOnMouseClicked(new EventHandler<Event>() {

			@Override
			public void handle(Event arg0) {

				Main.getLoading().setNotification("Getting Case Data...");
				Main.getController().changeCenter(Main.getLoading());
				IntakePane temp = new IntakePane(CaseBuilder.build(p), false);
				Main.setCase(p);
				Main.setIntake(temp);
				Main.getController().changeLeft(true);
				Main.getController().changeTop(new TopBar(p.getCaseStatusId(), temp));
				Main.getController().changeBottom(null);

			}
		});

		Label caseNameLabel = new Label(p.getCaseName());
		caseNameLabel.setStyle("-fx-font-size: 24; -font-weight: bold");

		clientBox.getChildren().add(caseNameLabel);
		Label dateTitle = new Label("Date of Intake:");

		Label createdBy = new Label("By: " + p.getOfficeIntakePerson().getNameFull());

		VBox detailsBox = new VBox();
		detailsBox.getChildren().add(dateTitle);

		if (p.getCallerDate() != null) {
			Label dateCreated = new Label(p.getCallerDate().format(DateTimeFormatter.ofPattern("MM/dd/YYYY")));
			detailsBox.getChildren().add(dateCreated);
		}

		detailsBox.getChildren().add(createdBy);
		detailsBox.setAlignment(Pos.CENTER);
		detailsBox.setSpacing(1);
		detailsBox.setMinWidth(75);

		clientBox.getChildren().add(detailsBox);

		return clientBox;
	}

	public void searching(String searchCriteria, int sortMode, boolean myCases) {
		this.getChildren().clear();
		setupList(searchCriteria.toLowerCase(), sortMode, myCases);
	}

	public ArrayList<HBox> getBoxes() {
		return boxes;
	}

}
