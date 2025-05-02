package application;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;

public class InputPaneController {
	@FXML
	private BorderPane borderPane = new BorderPane();
	private ScrollPane centerScroll;
	private PotentialsList potentialsList = new PotentialsList();
	FlowPane bottomBar = new FlowPane();
	ScrollPane menuScroll = new ScrollPane(new MenuList(false));

	public void initialize() {
		Main.setController(this);
		centerScroll = new ScrollPane();
		centerScroll.setFitToWidth(true);
		centerScroll.setFitToHeight(true);
		borderPane.setCenter(centerScroll);

		menuScroll.setPrefWidth(220);
		menuScroll.setHbarPolicy(ScrollBarPolicy.NEVER);
		borderPane.setLeft(menuScroll);

		bottomBar.setHgap(10);
		bottomBar.setVgap(10);
		bottomBar.setPadding(new Insets(10));
		bottomBar.setAlignment(Pos.CENTER_LEFT);
		borderPane.setBottom(bottomBar);

		changeCenter(potentialsList);
	}

	public void changeTop(TopBar bar) {
		borderPane.setTop(bar);
	}

	public void changeCenter(FlowPane node) {
		if (node.getClass() == PotentialsList.class) {
			bottomBar.getChildren().clear();
			changeBottom(new SearchBar((PotentialsList) node));
		} else
			changeBottom(null);
		centerScroll.setContent(node);
	}

	public void changeBottom(SearchBar bar) {
		if (bar == null)
			bottomBar.getChildren().clear();
		else
			bottomBar.getChildren().add(bar);
	}

	public void changeLeft(boolean showBack) {
		menuScroll.setContent(new MenuList(showBack));

	}

}
