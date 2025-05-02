package GUIElements;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;

public class AlreadyRunning extends FlowPane {

	public AlreadyRunning(Stage stage) {
		this.setAlignment(Pos.CENTER);
		this.setBackground(new Background(new BackgroundFill(Color.TEAL, null, getInsets())));

		Button yes = new Button("Yes");
		yes.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				stage.close();
			}
		});
		Button no = new Button("No");
		no.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				Platform.exit();
				System.exit(0);
			}
		});
		
		yes.setPrefSize(100, 50);
		no.setPrefSize(100, 50);
		
		

		HBox hb = new HBox();
		hb.setAlignment(Pos.CENTER);
		hb.setSpacing(10);
		hb.setPadding(new Insets(10));
		hb.getChildren().add(yes);
		hb.getChildren().add(no);

		VBox vb = new VBox();
		vb.setPadding(new Insets(10));
		vb.setAlignment(Pos.TOP_CENTER);
		Label label1 = new Label("Shale is already running");
		Label label2 = new Label("Would you like to start another instance?");

		label1.setFont(Font.font(24));
		label2.setFont(Font.font(20));

		vb.getChildren().add(label1);
		vb.getChildren().add(label2);
		vb.getChildren().add(hb);

		this.getChildren().add(vb);

	}
}
