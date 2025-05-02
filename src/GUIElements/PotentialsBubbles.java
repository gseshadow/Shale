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
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

public class PotentialsBubbles extends FlowPane {
	private ArrayList<Case> potentials = new ArrayList<>();
//	private ArrayList<Case> accepted = new ArrayList<>();
	private ArrayList<Case> denied = new ArrayList<>();
	private ArrayList<Case> closed = new ArrayList<>();
	private ArrayList<Case> transferred = new ArrayList<>();

	private Comparator<Case> caseNameSort = (Case o1, Case o2) -> o1.getCaseName().toLowerCase().compareTo(o2.getCaseName().toLowerCase());
	private Comparator<Case> dateSort = (Case o1, Case o2) -> o1.getCallerDate().toString().compareTo(o2.getCallerDate().toString());
	private Comparator<Case> solSort = new Comparator<Case>() {

		@Override
		public int compare(Case o1, Case o2) {
			String o1s = "a";
			String o2s = "a";

			System.out.println(o1.getCaseName() + " : compared to : " + o2.getCaseName());
			if (o1.getIncidentStatuteOfLimitations() == null) {
				System.out.println(o1.getCaseName() + " : Null SOL");
				o1s = "b";
			}
			if (o2.getIncidentStatuteOfLimitations() == null) {
				System.out.println(o2.getCaseName() + " : Null SOL");
				o2s = "b";
			}
			if (o1.getIncidentStatuteOfLimitations() != null && o2.getIncidentStatuteOfLimitations() != null) {
				if (o1.getIncidentStatuteOfLimitations().isAfter(o2.getIncidentStatuteOfLimitations())) {
					o1s = "b";
				} else {
					o2s = "b";
				}
			}

			System.out.println(o1s.compareTo(o2s));
			return o1s.compareTo(o2s);

		}
	};

	ArrayList<BorderPane> boxes = new ArrayList<>();

	private BorderWidths borderWidths = new BorderWidths(3);
	private Insets insets = new Insets(1);
	private CornerRadii cornerRadii = new CornerRadii(3);

	private int width = 250;
	private int height = 100;

	public PotentialsBubbles() {

		this.setHgap(10);
		this.setVgap(10);
		this.setPadding(new Insets(5));
		this.setAlignment(Pos.TOP_LEFT);

		for (Case c : Main.getCases().values()) {
			/*
			 * 9 = Potential, 10 = Closed, 13 = Accepted, 14 = Denied, 15 = Trial, 16 = Settlement, 17
			 * = Active (Transferred)
			 */
			int statusId = c.getCaseStatusId();
			if (statusId == 10) {
				closed.add(c);
			} else if (statusId == 17) {
				transferred.add(c);
//			} else if (statusId == 13) {
//				accepted.add(c);
			} else if (statusId == 14) {
				denied.add(c);
			} else if (statusId == 9) {
				potentials.add(c);
			}
		}
//		setupList("", true, true, true, true);

	}

	private void setupList(String searchCriteria, boolean po, /* boolean ac, */boolean de, boolean cl, boolean tr, int sortMode, boolean myCases) {

		if (sortMode == 0) {
			Collections.sort(potentials, caseNameSort);
//			Collections.sort(accepted, caseNameSort);
			Collections.sort(denied, caseNameSort);
			Collections.sort(closed, caseNameSort);
			Collections.sort(transferred, caseNameSort);
		} else if (sortMode == 1) {
			Collections.sort(potentials, dateSort);
//			Collections.sort(accepted, dateSort);
			Collections.sort(denied, dateSort);
			Collections.sort(closed, dateSort);
			Collections.sort(transferred, dateSort);
		} else if (sortMode == 2) {
			Collections.sort(potentials, solSort);
//			Collections.sort(accepted, dateSort);
			Collections.sort(denied, solSort);
			Collections.sort(closed, solSort);
			Collections.sort(transferred, solSort);
		}

		if (po)
			this.getChildren().add(getIntakeFlowPane(potentials, "Potentials", Color.ORANGE, searchCriteria, myCases));
//		if (ac)
//			this.getChildren().add(getIntakeFlowPane(accepted, "Accepted", Color.GREEN, searchCriteria, al));
		if (tr)
			this.getChildren().add(getIntakeFlowPane(transferred, "Transferred", Color.DARKGREEN, searchCriteria, myCases));
		if (de)
			this.getChildren().add(getIntakeFlowPane(denied, "Denied", Color.RED, searchCriteria, myCases));
		if (cl)
			this.getChildren().add(getIntakeFlowPane(closed, "Closed", Color.LIGHTSEAGREEN, searchCriteria, myCases));

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
			if (myCases) { // Filter cases by attorney (current User)
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

	private BorderPane getPBox(Case p) {

		HBox titleBox = new HBox();
		titleBox.setSpacing(10);
		titleBox.setAlignment(Pos.TOP_CENTER);
		titleBox.setPadding(new Insets(10));

		BorderPane clientBox = new BorderPane();
		clientBox.setPadding(new Insets(10));

		clientBox.setMinSize(100, 50);
		clientBox.setPrefSize(width * 10 * (Main.getCurrentUser().getIconSize() / 25), height * 10 * (Main.getCurrentUser().getIconSize() / 25));
		boxes.add(clientBox);

		/*
		 * Set color based on practice area
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

		/*
		 * Set color based on responsible attorney
		 */
		clientBox.setBackground(new Background(new BackgroundFill(Color.GRAY, cornerRadii, insets)));
		if (p.getOfficeResponsibleAttorneyId() != 0) {
			User attorney = Main.getUsers().get(p.getOfficeResponsibleAttorneyId());
			if (!attorney.getColor().equals("")) {
				clientBox.setBackground(new Background(new BackgroundFill(Color.valueOf(attorney.getColor()), cornerRadii, insets)));
			}
		}

		clientBox.setBorder(new Border(new BorderStroke(p.getBorderColor(), BorderStrokeStyle.SOLID, cornerRadii, borderWidths, null)));
		clientBox.setOnMouseEntered(new EventHandler<Event>() {

			@Override
			public void handle(Event arg0) {
				clientBox.setCursor(Cursor.HAND);
				clientBox.setBorder(new Border(new BorderStroke(Color.WHITE, BorderStrokeStyle.SOLID, cornerRadii, borderWidths, null)));

			}
		});

		clientBox.setOnMouseExited(new EventHandler<Event>() {

			@Override
			public void handle(Event arg0) {
				clientBox.setCursor(Cursor.DEFAULT);
				clientBox.setBorder(new Border(new BorderStroke(p.getBorderColor(), BorderStrokeStyle.SOLID, cornerRadii, borderWidths, null)));

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

		titleBox.getChildren().add(caseNameLabel);

		Label dateTitle = new Label("Date of Intake:");
		Label createdBy = new Label("By: " + p.getOfficeIntakePerson().getNameFull());

		FlowPane detailPane = new FlowPane();
		detailPane.setAlignment(Pos.CENTER);
		detailPane.setPadding(insets);

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
		detailPane.getChildren().add(detailsBox);

		FlowPane signedPane = new FlowPane();
		signedPane.setAlignment(Pos.BOTTOM_LEFT);
		Label signed = new Label("Unsigned");
		signed.setBackground(new Background(new BackgroundFill(Color.DARKGRAY, cornerRadii, new Insets(-2))));
		signed.setBorder(new Border(new BorderStroke(Color.BLACK, BorderStrokeStyle.SOLID, cornerRadii, new BorderWidths(1), new Insets(-2))));
		if (p.isFeeAgreementSigned()) {
			signed.setText("Signed");
			signed.setBackground(new Background(new BackgroundFill(Color.GREEN, cornerRadii, new Insets(-2))));
		}
		signedPane.getChildren().add(signed);

		clientBox.setLeft(signedPane);
		clientBox.setRight(detailPane);
		clientBox.setTop(titleBox);

		return clientBox;
	}

	public void searching(String searchCriteria, boolean potential, /* boolean accepted, */ boolean denied, boolean closed, boolean transferred,
			int sortMode, boolean myCases) {
		this.getChildren().clear();
		setupList(searchCriteria.toLowerCase(), potential, /* accepted, */ denied, closed, transferred, sortMode, myCases);
	}

	public ArrayList<Case> getPotentials() {
		return potentials;
	}

//	public ArrayList<Case> getAccepted() {
//		return accepted;
//	}

	public ArrayList<Case> getRejected() {
		return denied;
	}

	public ArrayList<BorderPane> getBoxes() {
		return boxes;
	}

}
