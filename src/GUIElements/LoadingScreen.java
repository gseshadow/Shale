package GUIElements;

import java.io.File;

import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

public class LoadingScreen extends FlowPane {

	private ImageView loadingImageView;
	private Image loadingImage;
	private File loadingFile;

//	private ImageView shaleImageView;
//	private Image shaleImage;
//	private File shaleFile;

	private Label notificationLabel = new Label();

	public LoadingScreen() {

		this.setAlignment(Pos.CENTER);
		this.setPadding(new Insets(5));

		VBox box = new VBox();
		box.setSpacing(50);
		box.setAlignment(Pos.CENTER);

		loadingFile = new File("Local" + System.getProperty("file.separator") + "Loading.gif");
		loadingImage = new Image(loadingFile.toURI().toString());
		loadingImageView = new ImageView(loadingImage);
		loadingImageView.setScaleX(.5);
		loadingImageView.setScaleY(.5);

		notificationLabel.setBackground(new Background(new BackgroundFill(new Color(.6, .6, .6, .9), new CornerRadii(5), new Insets(-5))));
		TranslateTransition st = new TranslateTransition();
		st.setToY(300);
		st.setNode(notificationLabel);
		st.play();

		StackPane stack = new StackPane();

//		stack.getChildren().add(shaleImageView);
		stack.getChildren().add(loadingImageView);
		stack.getChildren().add(notificationLabel);

		box.getChildren().add(stack);
//		box.getChildren().add(notificationLabel);

		this.getChildren().add(box);

	}

	public void setNotification(String message) {
		Platform.runLater(new Runnable() {

			@Override
			public void run() {
				notificationLabel.setText(message);

			}
		});

	}
}
