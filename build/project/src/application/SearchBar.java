package application;

import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;

public class SearchBar extends HBox {

	Button clear = new Button("Clear");
	TextField searchBox = new TextField();
	Label potentials = new Label("Potentials");
	Label accepted = new Label("Accepted");
	Label rejected = new Label("Rejected");
	RadioButton potButton = new RadioButton("Potential");
	RadioButton accButton = new RadioButton("Accepted");
	RadioButton rejButton = new RadioButton("Rejected");

	public SearchBar(PotentialsList list) {
		this.getChildren().add(searchBox);
		this.getChildren().add(clear);
		this.getChildren().add(potButton);
		this.getChildren().add(accButton);
		this.getChildren().add(rejButton);
		this.setAlignment(Pos.CENTER_LEFT);
		this.setSpacing(10);

		clear.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				searchBox.setText("");
				list.searching("", potButton.isSelected(), accButton.isSelected(), rejButton.isSelected());
			}
		});

		searchBox.setPromptText("Search");
		searchBox.setMinWidth(150);
		searchBox.setMinHeight(25);
		searchBox.setOnKeyTyped(new EventHandler<Event>() {

			@Override
			public void handle(Event arg0) {
				list.searching(searchBox.getText(), potButton.isSelected(), accButton.isSelected(),
						rejButton.isSelected());

			}
		});

		potButton.setSelected(true);
		accButton.setSelected(true);
		rejButton.setSelected(true);

		potButton.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				list.searching(searchBox.getText(), potButton.isSelected(), accButton.isSelected(),
						rejButton.isSelected());

			}
		});
		accButton.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				list.searching(searchBox.getText(), potButton.isSelected(), accButton.isSelected(),
						rejButton.isSelected());

			}
		});
		rejButton.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				list.searching(searchBox.getText(), potButton.isSelected(), accButton.isSelected(),
						rejButton.isSelected());

			}
		});

	}
}
