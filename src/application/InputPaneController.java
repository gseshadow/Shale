package application;

import GUIElements.ActiveCaseView;
import GUIElements.FacilitiesView;
import GUIElements.LoadingScreen;
import GUIElements.Login;
import GUIElements.MenuList;
import GUIElements.PotentialsBubbles;
import GUIElements.PotentialsList;
import GUIElements.SearchBar;
import GUIElements.TopBar;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.image.Image;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundImage;
import javafx.scene.layout.BackgroundPosition;
import javafx.scene.layout.BackgroundRepeat;
import javafx.scene.layout.BackgroundSize;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;

public class InputPaneController {
	@FXML
	private BorderPane borderPane = new BorderPane();
	private ScrollPane centerScroll = new ScrollPane();
	private FlowPane bottomBar = new FlowPane();
	private SearchBar searchBar = new SearchBar();

	public void initialize() {

		changeBackground(Main.getBackgroundImage());

		Main.setController(this);
		centerScroll.setFitToWidth(true);
		centerScroll.setFitToHeight(true);
		centerScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
		borderPane.setCenter(centerScroll);

		bottomBar.setHgap(10);
		bottomBar.setVgap(10);
		bottomBar.setPadding(new Insets(10));
		bottomBar.setAlignment(Pos.CENTER_LEFT);
		borderPane.setBottom(bottomBar);

		Login login = new Login();
		if (!Main.getCurrentUser().isStayLoggedIn()) {
			changeCenter(login);
		}
	}

	public void changeTop(TopBar bar) {
		borderPane.setTop(bar);
	}

	public void changeCenter(Node node) {

		if (node.getClass() != LoadingScreen.class)
			hideScrollBars(false);

		if (node.getClass() == PotentialsBubbles.class) {// Show bubble view
			bottomBar.getChildren().clear();
			searchBar = new SearchBar((PotentialsBubbles) node);
			changeBottom(searchBar);
		} else if (node.getClass() == PotentialsList.class) {// Show list view
			bottomBar.getChildren().clear();
			searchBar = new SearchBar((PotentialsList) node);
			changeBottom(searchBar);
		} else if (node.getClass() == ActiveCaseView.class) {// Show active case view
			bottomBar.getChildren().clear();
			searchBar = new SearchBar((ActiveCaseView) node);
			changeBottom(searchBar);
		} else if (node.getClass() == FacilitiesView.class) {// Show facilitiess view
			bottomBar.getChildren().clear();
			searchBar = new SearchBar();

		} else

		{
			changeBottom(null);
		}
		centerScroll.setContent(node);

	}

	public void changeBottom(SearchBar bar) {
		searchBar = bar;
		if (bar == null) {
			bottomBar.getChildren().clear();
			bottomBar.setBackground(null);
		} else {
			bottomBar.getChildren().add(bar);
			bottomBar.setBackground(Main.getMenuBackground());
		}
	}

	public SearchBar getSearchBar() {
		return searchBar;
	}

	public void changeLeft(boolean showBack) {
		borderPane.setLeft(new MenuList(showBack, Main.getCurrentUser().isShowActiveView()));

	}

	public void changeLeft(Node node) {
		borderPane.setLeft(node);

	}

	public void changeBackground(Image image) {
		borderPane.setBackground(new Background(new BackgroundImage(image, BackgroundRepeat.NO_REPEAT, BackgroundRepeat.NO_REPEAT,
				BackgroundPosition.CENTER, new BackgroundSize(1.0, 1.0, true, true, false, false))));
	}

	public Class<? extends Node> getCenterClass() {
		return centerScroll.getContent().getClass();
	}

	public PotentialsBubbles getBubbles() {
		return (PotentialsBubbles) centerScroll.getContent();
	}

	public ActiveCaseView getActiveView() {
		return (ActiveCaseView) centerScroll.getContent();
	}

	public Node getCenter() {
		return centerScroll.getContent();
	}

	public void hideScrollBars(boolean b) {
		if (b) {
			centerScroll.setVbarPolicy(ScrollBarPolicy.NEVER);
			centerScroll.setHbarPolicy(ScrollBarPolicy.NEVER);
		} else {
			centerScroll.setVbarPolicy(ScrollBarPolicy.AS_NEEDED);
			centerScroll.setHbarPolicy(ScrollBarPolicy.AS_NEEDED);
		}

	}

}
