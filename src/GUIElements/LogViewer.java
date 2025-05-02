
package GUIElements;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import application.Main;
import connections.Server;
import dataStructures.Case;
import dataStructures.Contact;
import dataStructures.LogEntry;
import dataStructures.User;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

public class LogViewer extends BorderPane {
	VBox searchBox = new VBox();
	ListView<LogEntry> list = new ListView<LogEntry>();

	public LogViewer() {

		setupSearch();

		this.setLeft(searchBox);
		this.setCenter(list);

	}

	public class ContactComparator implements Comparator<Contact> {

		@Override
		public int compare(Contact o1, Contact o2) {
			return o1.getName_last().compareTo(o2.getName_last());
		}

	}

	public class CaseComparator implements Comparator<Case> {

		@Override
		public int compare(Case o1, Case o2) {
			return o1.getCaseName().compareTo(o2.getCaseName());
		}

	}

	public class UserComparator implements Comparator<User> {

		@Override
		public int compare(User o1, User o2) {
			return o1.getNameLast().compareTo(o2.getNameLast());
		}

	}

	private void setupSearch() {
		/**
		 */
		ArrayList<Contact> contacts = new ArrayList<Contact>();
		for (Contact c : Main.getContacts().values()) {
			contacts.add(c);
		}
		Collections.sort(contacts, new ContactComparator());

		ArrayList<Case> cses = new ArrayList<Case>();
		for (Case c : Main.getCases().values()) {
			cses.add(c);
		}
		Collections.sort(cses, new CaseComparator());

		ArrayList<User> users = new ArrayList<User>();
		for (User c : Main.getUsers().values()) {
			users.add(c);
		}
		Collections.sort(users, new UserComparator());
		/**
		 * 
		 */

		searchBox.setMinWidth(100);
		searchBox.setAlignment(Pos.TOP_LEFT);
		searchBox.setSpacing(10);

		Label usr = new Label("Select User:");
		searchBox.getChildren().add(usr);

		ChoiceBox<User> selectUser = new ChoiceBox<User>();
		for (User u : users) {
			if (!u.isIs_deleted())
				selectUser.getItems().add(u);
		}
		selectUser.setMinWidth(80);
		searchBox.getChildren().add(selectUser);

		Label cse = new Label("Select Case:");
		searchBox.getChildren().add(cse);

		ChoiceBox<Case> selectCase = new ChoiceBox<Case>();
		for (Case u : cses) {
			if (!u.isDeleted())
				selectCase.getItems().add(u);
		}
		selectCase.setMinWidth(80);
		searchBox.getChildren().add(selectCase);

		Label con = new Label("Select Contact:");
		searchBox.getChildren().add(con);

		ChoiceBox<Contact> selectContact = new ChoiceBox<Contact>();
		for (Contact u : contacts) {
			if (!u.isIs_deleted())
				selectContact.getItems().add(u);
		}
		selectContact.setMinWidth(80);
		searchBox.getChildren().add(selectContact);

		Button search = new Button("Search");
		search.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent event) {

				int userValue = 0;
				if (selectUser.getValue() != null) {
					userValue = selectUser.getValue().get_id();
				}
				int caseValue = 0;
				if (selectCase.getValue() != null) {
					caseValue = selectCase.getValue().get_id();
				}
				int contactValue = 0;
				if (selectContact.getValue() != null) {
					contactValue = selectContact.getValue().get_id();
				}
				list.getItems().clear();
				for (LogEntry l : Server.getAuditSearchResults(userValue, caseValue, contactValue)) {
					list.getItems().add(l);
				}
			}
		});
		searchBox.getChildren().add(search);
	}

}
