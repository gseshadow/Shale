package GUIElements;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import application.Main;
import dataStructures.Case;
import dataStructures.CaseBuilder;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

public class PotentialsList extends ListView<Label> {
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
			if (o1.getIncidentStatuteOfLimitations() == null) {
				return 0;
			}
			if (o2.getIncidentStatuteOfLimitations() == null) {
				return 1;
			}
			return o1.getIncidentStatuteOfLimitations().toString().compareTo(o2.getIncidentStatuteOfLimitations().toString());

		}
	};
	/*
	 * GUI variables
	 */
	private BorderWidths borderWidths = new BorderWidths(1);
	private Insets insets = new Insets(-3);
	private CornerRadii cornerRadii = new CornerRadii(3);

	/*
	 * Files used when accessing local storage
	 */
	File potentialsFile = new File(Main.getFileLocation() + System.getProperty("file.separator") + "Potentials");
	File[] potentialsFiles = potentialsFile.listFiles();

	File acceptedFile = new File(Main.getFileLocation() + System.getProperty("file.separator") + "Accepted");
	File[] acceptedFiles = acceptedFile.listFiles();

	File rejectedFile = new File(Main.getFileLocation() + System.getProperty("file.separator") + "Rejected");
	File[] rejectedFiles = rejectedFile.listFiles();

	public PotentialsList() {
		for (Case c : Main.getCases().values()) {
			/*
			 * 9 = Potential 10 = Closed 13 = Accepted 14 = Denied 15 = Trial 16 = Settlement, 17 =
			 * Accepted (Transferred)
			 */
			int statusId = c.getCaseStatusId();
			if (statusId == 9) {
				potentials.add(c);
//			} else if (statusId == 13) {
//				accepted.add(c);
			} else if (statusId == 14) {
				denied.add(c);
			} else if (statusId == 10) {
				closed.add(c);
			} else if (statusId == 17) {
				transferred.add(c);
			}
		}

	}

	private void setupList(String searchCriteria, boolean po, /* boolean ac, */ boolean de, boolean cl, boolean tr, int sortMode, boolean myCases) {
		this.getItems().clear();

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
			addItems(potentials, "Potentials", searchCriteria, myCases);
//		if (ac)
//			addItems(accepted, "Accepted", searchCriteria);
		if (tr)
			addItems(transferred, "Transferred", searchCriteria, myCases);
		if (de)
			addItems(denied, "Denied", searchCriteria, myCases);
		if (cl)
			addItems(closed, "Closed", searchCriteria, myCases);

	}

	private void addItems(ArrayList<Case> list, String header, String searchCriteria, boolean myCases) {
		Label headerLabel = new Label(header);
		headerLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 18");
		headerLabel.setUnderline(true);
		this.getItems().add(headerLabel);

		for (Case p : list) {

			if (myCases) {
				if (p.getOfficeResponsibleAttorneyId() == Main.getCurrentUser().get_id()) {
					if (p.getCaseName().toLowerCase().contains(searchCriteria)) {
						Label temp = new Label(p.getCaseName());
						temp.setFont(Font.font(16));
						temp.setOnMouseEntered(new EventHandler<Event>() {

							@Override
							public void handle(Event arg0) {
								temp.setBorder(new Border(new BorderStroke(Color.RED, BorderStrokeStyle.SOLID, cornerRadii, borderWidths, insets)));
								temp.setCursor(Cursor.HAND);
							}
						});
						temp.setOnMouseExited(new EventHandler<Event>() {

							@Override
							public void handle(Event arg0) {
								temp.setBorder(null);
								temp.setCursor(Cursor.DEFAULT);
							}
						});
						temp.setOnMouseClicked(new EventHandler<Event>() {

							@Override
							public void handle(Event arg0) {

								IntakePane temp = new IntakePane(CaseBuilder.build(p), false);
								Main.setCase(p);
								Main.setIntake(temp);
								Main.getController().changeCenter(temp);
								Main.getController().changeLeft(true);
								Main.getController().changeTop(new TopBar(p.getCaseStatusId(), temp));
								Main.getController().changeBottom(null);

							}
						});
						this.getItems().add(temp);
					}
				}
			} else if (p.getCaseName().toLowerCase().contains(searchCriteria)) {
				Label temp = new Label(p.getCaseName());
				temp.setFont(Font.font(16));
				temp.setOnMouseEntered(new EventHandler<Event>() {

					@Override
					public void handle(Event arg0) {
						temp.setBorder(new Border(new BorderStroke(Color.RED, BorderStrokeStyle.SOLID, cornerRadii, borderWidths, insets)));
						temp.setCursor(Cursor.HAND);
					}
				});
				temp.setOnMouseExited(new EventHandler<Event>() {

					@Override
					public void handle(Event arg0) {
						temp.setBorder(null);
						temp.setCursor(Cursor.DEFAULT);
					}
				});
				temp.setOnMouseClicked(new EventHandler<Event>() {

					@Override
					public void handle(Event arg0) {

						IntakePane temp = new IntakePane(CaseBuilder.build(p), false);
						Main.setCase(p);
						Main.setIntake(temp);
						Main.getController().changeCenter(temp);
						Main.getController().changeLeft(true);
						Main.getController().changeTop(new TopBar(p.getCaseStatusId(), temp));
						Main.getController().changeBottom(null);

					}
				});
				this.getItems().add(temp);
			}
		}

	}

	public void searching(String searchCriteria, boolean potential, /* boolean accepted, */ boolean denied, boolean closed, boolean transferred,
			int sortMode, boolean myCases) {
		setupList(searchCriteria.toLowerCase(), potential, /* accepted, */ denied, closed, transferred, sortMode, myCases);

	}
}