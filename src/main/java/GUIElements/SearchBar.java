package GUIElements;

import java.util.ArrayList;

import application.Main;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;

public class SearchBar extends HBox {

	private ToggleButton myCases = new ToggleButton("My Cases");
	private Button clear = new Button("Clear");
	private TextField searchBox = new TextField();
	private RadioButton potButton = new RadioButton("Potential");
//	private RadioButton accButton = new RadioButton("Accepted");
	private RadioButton denButton = new RadioButton("Denied");
	private RadioButton cloButton = new RadioButton("Closed");
	private RadioButton traButton = new RadioButton("Transferred");
	private Label sortBy = new Label("  |    Sort By:");
	private ToggleGroup sortGroup = new ToggleGroup();
	private RadioButton alphabetical = new RadioButton("Name");
	private RadioButton chronological = new RadioButton("Date");
	private RadioButton sol = new RadioButton("Statute of Lim.");
	private Label showAsList = new Label("  |    Show as List");
	private RadioButton listButton = new RadioButton();
	private int sortMode = 0;

	private int width = 250;
	private int height = 100;

	public SearchBar() {

	}

	public SearchBar(PotentialsBubbles node) {

		this.getChildren().add(myCases);
		this.getChildren().add(searchBox);
		this.getChildren().add(clear);
		this.getChildren().add(potButton);
//		this.getChildren().add(accButton);
		this.getChildren().add(denButton);
		this.getChildren().add(cloButton);
		this.getChildren().add(traButton);
		this.getChildren().add(sortBy);
		this.getChildren().add(alphabetical);
		this.getChildren().add(chronological);
		this.getChildren().add(sol);
		this.getChildren().add(showAsList);
		this.getChildren().add(listButton);

		HBox sizeBox = new HBox();
		sizeBox.setSpacing(5);
		sizeBox.setPadding(new Insets(10));
		sizeBox.setAlignment(Pos.CENTER_LEFT);

		Slider sizeSlider = new Slider(1, 10, Main.getCurrentUser().getIconSize());
		sizeSlider.setShowTickMarks(true);
		sizeSlider.setMajorTickUnit(10f);
		sizeSlider.setShowTickLabels(true);
		sizeSlider.setPrefWidth(250);
		sizeSlider.setOnMouseDragged(new EventHandler<Event>() {

			@Override
			public void handle(Event arg0) {
				Main.getCurrentUser().setIconSize(sizeSlider.getValue());

				for (BorderPane h : Main.getController().getBubbles().getBoxes()) {
					h.setPrefSize(width * 10 * (sizeSlider.getValue() / 25), height * 10 * (sizeSlider.getValue() / 25));
				}
			}
		});

		sizeSlider.setOnMouseClicked(new EventHandler<Event>() {

			@Override
			public void handle(Event arg0) {
				Main.getCurrentUser().setIconSize(sizeSlider.getValue());

				for (BorderPane h : Main.getController().getBubbles().getBoxes()) {
					h.setPrefSize(width * 10 * (sizeSlider.getValue() / 25), height * 10 * (sizeSlider.getValue() / 25));
				}
			}
		});

		Label l = new Label("Tile Size:");
		l.setStyle("-fx-text-fill: white");
		sizeBox.getChildren().add(l);
		sizeBox.getChildren().add(sizeSlider);

		this.getChildren().add(sizeBox);

		ArrayList<RadioButton> rbs = new ArrayList<>();
		rbs.add(potButton);
//		rbs.add(accButton);
		rbs.add(denButton);
		rbs.add(cloButton);
		rbs.add(traButton);
		rbs.add(alphabetical);
		rbs.add(chronological);
		rbs.add(sol);
		rbs.add(listButton);

		for (RadioButton rb : rbs) {
			rb.setTextFill(Color.WHITE);
		}
		sortBy.setTextFill(Color.WHITE);
		showAsList.setTextFill(Color.WHITE);

		this.setAlignment(Pos.CENTER_LEFT);
		this.setSpacing(10);

		if (!Main.getCurrentUser().isIs_attorney())
			myCases.setVisible(false);
		myCases.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				searchingBubbles(node);

			}
		});

		clear.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				searchBox.setText("");
				node.searching("", potButton.isSelected(), /* accButton.isSelected(), */ denButton.isSelected(), cloButton.isSelected(),
						traButton.isSelected(), sortMode, myCases.isSelected());
			}
		});

		searchBox.setPromptText("Search");
		searchBox.setMinWidth(150);
		searchBox.setMinHeight(25);
		searchBox.setOnKeyTyped(new EventHandler<Event>() {

			@Override
			public void handle(Event arg0) {
				searchingBubbles(node);

			}
		});

		potButton.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				searchingBubbles(node);
				Main.getCurrentUser().setShowPotential(potButton.isSelected());
			}
		});
//		accButton.setOnAction(new EventHandler<ActionEvent>() {
//
//			@Override
//			public void handle(ActionEvent arg0) {
//				searchingBubbles(node);
//				Main.getCurrentUser().setShowAccepted(accButton.isSelected());
//			}
//		});
		denButton.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				searchingBubbles(node);
				Main.getCurrentUser().setShowDenied(denButton.isSelected());
			}
		});

		cloButton.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				searchingBubbles(node);
				Main.getCurrentUser().setShowClosed(cloButton.isSelected());
			}
		});

		traButton.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				searchingBubbles(node);
				Main.getCurrentUser().setShowTransferred(traButton.isSelected());
			}
		});

		potButton.setSelected(Main.getCurrentUser().isShowPotential());
//		accButton.setSelected(Main.getCurrentUser().isShowAccepted());
		denButton.setSelected(Main.getCurrentUser().isShowDenied());
		cloButton.setSelected(Main.getCurrentUser().isShowClosed());
		traButton.setSelected(Main.getCurrentUser().isShowTransferred());

		alphabetical.setSelected(true);
		alphabetical.setToggleGroup(sortGroup);
		alphabetical.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				sortMode = 0;
				searchingBubbles(node);
			}
		});
		chronological.setToggleGroup(sortGroup);
		chronological.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				sortMode = 1;
				searchingBubbles(node);
			}
		});

		sol.setToggleGroup(sortGroup);
		sol.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				sortMode = 2;
				searchingBubbles(node);
			}
		});

		listButton.setSelected(false);
		listButton.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				Main.getCurrentUser().setShowAsList(listButton.isSelected());

				Main.getController().changeBottom(new SearchBar(new PotentialsList()));
				if (listButton.isSelected()) {
					Main.getController().changeCenter(new PotentialsList());
					Main.getCurrentUser().setShowAsList(true);
				} else {
					Main.getController().changeCenter(new PotentialsBubbles());
					Main.getCurrentUser().setShowAsList(false);
				}

			}
		});
		searchingBubbles(node);
	}

	private void searchingBubbles(PotentialsBubbles node) {
		node.searching(searchBox.getText(), potButton.isSelected(), /* accButton.isSelected(), */ denButton.isSelected(), cloButton.isSelected(),
				traButton.isSelected(), sortMode, myCases.isSelected());
	}

	public SearchBar(ActiveCaseView node) {
		this.getChildren().add(myCases);
		this.getChildren().add(searchBox);
		this.getChildren().add(clear);
		this.getChildren().add(sortBy);
		this.getChildren().add(alphabetical);
		this.getChildren().add(chronological);

		if (!Main.getCurrentUser().isIs_attorney())
			myCases.setVisible(false);
		myCases.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				searchingActive(node);

			}
		});

		HBox sizeBox = new HBox();
		sizeBox.setSpacing(5);
		sizeBox.setPadding(new Insets(10));
		sizeBox.setAlignment(Pos.CENTER_LEFT);

		Slider sizeSlider = new Slider(1, 10, Main.getCurrentUser().getIconSize());
		sizeSlider.setShowTickMarks(true);
		sizeSlider.setMajorTickUnit(10f);
		sizeSlider.setShowTickLabels(true);
		sizeSlider.setPrefWidth(250);
		sizeSlider.setOnMouseDragged(new EventHandler<Event>() {

			@Override
			public void handle(Event arg0) {
				Main.getCurrentUser().setIconSize(sizeSlider.getValue());

				for (HBox h : Main.getController().getActiveView().getBoxes()) {
					h.setPrefSize(width * 10 * (sizeSlider.getValue() / 25), height * 10 * (sizeSlider.getValue() / 25));
				}
			}
		});

		Label l = new Label("Tile Size:");
		l.setStyle("-fx-text-fill: white");
		sizeBox.getChildren().add(l);
		sizeBox.getChildren().add(sizeSlider);

		this.getChildren().add(sizeBox);

		ArrayList<RadioButton> rbs = new ArrayList<>();
		rbs.add(potButton);
//		rbs.add(accButton);
		rbs.add(denButton);
		rbs.add(cloButton);
		rbs.add(traButton);
		rbs.add(alphabetical);
		rbs.add(chronological);
		rbs.add(sol);
		rbs.add(listButton);

		for (RadioButton rb : rbs) {
			rb.setTextFill(Color.WHITE);
		}
		sortBy.setTextFill(Color.WHITE);
		showAsList.setTextFill(Color.WHITE);

		this.setAlignment(Pos.CENTER_LEFT);
		this.setSpacing(10);

		clear.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				searchBox.setText("");
				node.searching("", sortMode, myCases.isSelected());
			}
		});

		searchBox.setPromptText("Search");
		searchBox.setMinWidth(150);
		searchBox.setMinHeight(25);
		searchBox.setOnKeyTyped(new EventHandler<Event>() {

			@Override
			public void handle(Event arg0) {
				searchingActive(node);

			}
		});

		potButton.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				searchingActive(node);
				Main.getCurrentUser().setShowPotential(potButton.isSelected());
			}
		});
//		accButton.setOnAction(new EventHandler<ActionEvent>() {
//
//			@Override
//			public void handle(ActionEvent arg0) {
//				searchingBubbles(node);
//				Main.getCurrentUser().setShowAccepted(accButton.isSelected());
//			}
//		});
		denButton.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				searchingActive(node);
				Main.getCurrentUser().setShowDenied(denButton.isSelected());
			}
		});

		cloButton.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				searchingActive(node);
				Main.getCurrentUser().setShowClosed(cloButton.isSelected());
			}
		});

		traButton.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				searchingActive(node);
				Main.getCurrentUser().setShowTransferred(traButton.isSelected());
			}
		});

		potButton.setSelected(Main.getCurrentUser().isShowPotential());
//		accButton.setSelected(Main.getCurrentUser().isShowAccepted());
		denButton.setSelected(Main.getCurrentUser().isShowDenied());
		cloButton.setSelected(Main.getCurrentUser().isShowClosed());
		traButton.setSelected(Main.getCurrentUser().isShowTransferred());

		alphabetical.setSelected(true);
		alphabetical.setToggleGroup(sortGroup);
		alphabetical.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				sortMode = 0;
				searchingActive(node);

			}
		});
		chronological.setToggleGroup(sortGroup);
		chronological.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				sortMode = 1;
				searchingActive(node);
			}
		});

		sol.setToggleGroup(sortGroup);
		sol.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				sortMode = 2;
				searchingActive(node);
			}
		});

		listButton.setSelected(false);
		listButton.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				Main.getCurrentUser().setShowAsList(listButton.isSelected());

				Main.getController().changeBottom(new SearchBar(new PotentialsList()));
				if (listButton.isSelected()) {
					Main.getController().changeCenter(new PotentialsList());
					Main.getCurrentUser().setShowAsList(true);
				} else {
					Main.getController().changeCenter(new PotentialsBubbles());
					Main.getCurrentUser().setShowAsList(false);
				}

			}
		});
		searchingActive(node);
	}

	private void searchingActive(ActiveCaseView node) {
		node.searching(searchBox.getText(), sortMode, myCases.isSelected());
	}

	public SearchBar(PotentialsList node) {
		this.getChildren().add(myCases);
		this.getChildren().add(searchBox);
		this.getChildren().add(clear);
		this.getChildren().add(potButton);
//		this.getChildren().add(accButton);
		this.getChildren().add(denButton);
		this.getChildren().add(cloButton);
		this.getChildren().add(traButton);
		this.getChildren().add(sortBy);
		this.getChildren().add(alphabetical);
		this.getChildren().add(chronological);
		this.getChildren().add(sol);
		this.getChildren().add(showAsList);
		this.getChildren().add(listButton);
		ArrayList<RadioButton> rbs = new ArrayList<>();
		rbs.add(potButton);
//		rbs.add(accButton);
		rbs.add(denButton);
		rbs.add(cloButton);
		rbs.add(traButton);
		rbs.add(alphabetical);
		rbs.add(chronological);
		rbs.add(sol);
		rbs.add(listButton);
		for (RadioButton rb : rbs) {
			rb.setTextFill(Color.WHITE);
		}
		sortBy.setTextFill(Color.WHITE);
		showAsList.setTextFill(Color.WHITE);
		this.setAlignment(Pos.CENTER_LEFT);
		this.setSpacing(10);

		if (!Main.getCurrentUser().isIs_attorney())
			myCases.setVisible(false);
		myCases.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				searchingList(node);

			}
		});

		clear.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				searchBox.setText("");
				node.searching("", potButton.isSelected(), /* accButton.isSelected(), */denButton.isSelected(), cloButton.isSelected(),
						traButton.isSelected(), sortMode, myCases.isSelected());
			}
		});

		searchBox.setPromptText("Search");
		searchBox.setMinWidth(150);
		searchBox.setMinHeight(25);
		searchBox.setOnKeyTyped(new EventHandler<Event>() {

			@Override
			public void handle(Event arg0) {

				searchingList(node);

			}
		});

		potButton.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				searchingList(node);
				Main.getCurrentUser().setShowPotential(potButton.isSelected());
			}
		});
//		accButton.setOnAction(new EventHandler<ActionEvent>() {
//
//			@Override
//			public void handle(ActionEvent arg0) {
//				searchingList(node);
//				Main.getCurrentUser().setShowAccepted(accButton.isSelected());
//			}
//		});
		denButton.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				searchingList(node);
				Main.getCurrentUser().setShowDenied(denButton.isSelected());
			}
		});

		cloButton.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				searchingList(node);
				Main.getCurrentUser().setShowClosed(cloButton.isSelected());
			}
		});

		traButton.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				searchingList(node);
				Main.getCurrentUser().setShowTransferred(traButton.isSelected());
			}
		});

		potButton.setSelected(Main.getCurrentUser().isShowPotential());
//		accButton.setSelected(Main.getCurrentUser().isShowAccepted());
		denButton.setSelected(Main.getCurrentUser().isShowDenied());
		cloButton.setSelected(Main.getCurrentUser().isShowClosed());
		traButton.setSelected(Main.getCurrentUser().isShowTransferred());

		alphabetical.setSelected(true);
		alphabetical.setToggleGroup(sortGroup);
		alphabetical.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				sortMode = 0;
				searchingList(node);
			}
		});
		chronological.setToggleGroup(sortGroup);
		chronological.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				sortMode = 1;
				searchingList(node);
			}
		});

		sol.setToggleGroup(sortGroup);
		sol.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				sortMode = 2;
				searchingList(node);
			}
		});

		listButton.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				Main.getCurrentUser().setShowAsList(listButton.isSelected());
				Main.getController().changeBottom(new SearchBar(new PotentialsBubbles()));
				if (listButton.isSelected()) {
					Main.getController().changeCenter(new PotentialsList());
				} else {
					Main.getController().changeCenter(new PotentialsBubbles());
				}

			}
		});
		listButton.setSelected(true);

		searchingList(node);
	}

	private void searchingList(PotentialsList node) {
		node.searching(searchBox.getText(), potButton.isSelected(), /* accButton.isSelected(), */denButton.isSelected(), cloButton.isSelected(),
				traButton.isSelected(), sortMode, myCases.isSelected());
	}

}
