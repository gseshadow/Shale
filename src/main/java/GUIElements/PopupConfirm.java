package GUIElements;

import application.Main;
import connections.ConnectionResources;
import connections.Server;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
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
import javafx.scene.text.Font;

public class PopupConfirm extends FlowPane {

	public PopupConfirm(String prompt, IntakePane intake) {
		this.setAlignment(Pos.CENTER);
		this.setPadding(new Insets(10));
		this.setHgap(10);
		this.setVgap(10);

		VBox popBox = new VBox();
		popBox.setPrefSize(500, 250);
		popBox.setBackground(new Background(new BackgroundFill(Color.DARKGRAY, new CornerRadii(3), new Insets(1))));
		popBox.setBorder(new Border(new BorderStroke(Color.RED, BorderStrokeStyle.SOLID, new CornerRadii(3), new BorderWidths(10), new Insets(-1))));
		popBox.setSpacing(10);
		popBox.setPadding(new Insets(10));
		popBox.setAlignment(Pos.CENTER);

		HBox buttonBox = new HBox();
		buttonBox.setSpacing(10);
		buttonBox.setPadding(new Insets(10));
		buttonBox.setAlignment(Pos.CENTER);

		Label confirm = new Label(prompt);
		confirm.setAlignment(Pos.CENTER);
		confirm.setFont(new Font(18));

		Button yes = new Button("Yes");
		setupButton(yes, Color.DARKRED);
		yes.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				Main.getController().changeTop(null);
				Main.getController().changeLeft(false);
				Main.updateCases();
				if (Main.getCurrentUser().isShowAsList()) {
					Main.getController().changeCenter(new PotentialsList());
				} else
					Main.getController().changeCenter(new PotentialsBubbles());
			}
		});

		Button no = new Button("No");
		setupButton(no, Color.DARKGREEN);
		no.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				Main.getController().changeLeft(true);
				Main.getController().changeCenter(intake);
			}
		});

		buttonBox.getChildren().add(yes);
		buttonBox.getChildren().add(no);
		popBox.getChildren().add(confirm);
		popBox.getChildren().add(buttonBox);
		this.getChildren().add(popBox);

	}

	public PopupConfirm(String prompt, IntakePane intake, boolean toDelete) {
		this.setAlignment(Pos.CENTER);
		this.setPadding(new Insets(10));
		this.setHgap(10);
		this.setVgap(10);

		VBox popBox = new VBox();
		popBox.setPrefSize(500, 250);
		popBox.setBackground(new Background(new BackgroundFill(Color.DARKGRAY, new CornerRadii(3), new Insets(1))));
		popBox.setBorder(new Border(new BorderStroke(Color.RED, BorderStrokeStyle.SOLID, new CornerRadii(3), new BorderWidths(10), new Insets(-1))));
		popBox.setSpacing(10);
		popBox.setPadding(new Insets(10));
		popBox.setAlignment(Pos.CENTER);

		HBox buttonBox = new HBox();
		buttonBox.setSpacing(10);
		buttonBox.setPadding(new Insets(10));
		buttonBox.setAlignment(Pos.CENTER);

		Label confirm = new Label(prompt);
		confirm.setAlignment(Pos.CENTER);
		confirm.setFont(new Font(18));

		Button yes = new Button("Yes");
		setupButton(yes, Color.DARKRED);
		yes.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				Main.setIntake(null);
				Main.setCase(null);

				int id = intake.getCase().get_id();
//				System.out.println(id + " : Confirm");// TODO
//				for (Case c : Main.getCases().values()) {// TODO
//					System.out.println(c.get_id());
//				}
//				for (int k : Main.getCases().keySet()) {
//					System.out.println(k + " : Key");
//				}

				Main.getCases().remove(id);

//				System.out.println();
//				for (Case c : Main.getCases().values()) {// TODO
//					System.out.println(c.get_id());
//				}

				Main.getIncidents().remove(intake.getCase().getIncidentId());

				Main.getUpdater().publish(intake.getCase().get_id() + "#8#" + Main.getCurrentCase().get_id() + "#deleteCase#");
				Task<Integer> t = new Task<Integer>() {

					@Override
					protected Integer call() throws Exception {

						Server.deleteCase(intake.getCase().get_id(), ConnectionResources.getConnection());

						return null;
					}
				};
				new Thread(t).start();

				Main.getController().changeTop(null);
				Main.getController().changeLeft(false);

				if (Main.getCurrentUser().isShowAsList()) {
					Main.getController().changeCenter(new PotentialsList());
				} else
					Main.getController().changeCenter(new PotentialsBubbles());
			}
		});

		Button no = new Button("No");
		setupButton(no, Color.DARKGREEN);
		no.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				Main.getController().changeLeft(true);
				Main.getController().changeCenter(intake);
			}
		});

		buttonBox.getChildren().add(yes);
		buttonBox.getChildren().add(no);
		popBox.getChildren().add(confirm);
		popBox.getChildren().add(buttonBox);
		this.getChildren().add(popBox);

	}

	private void setupButton(Button b, Color color) {
		b.setTextFill(color);
		b.setPrefSize(200, 50);
		b.setBackground(new Background(new BackgroundFill(color, new CornerRadii(3), new Insets(0))));
		b.setBorder(new Border(new BorderStroke(Color.BLACK, BorderStrokeStyle.SOLID, new CornerRadii(3), new BorderWidths(2), new Insets(1))));
		b.setOnMouseEntered(new EventHandler<Event>() {

			@Override
			public void handle(Event arg0) {
				b.setCursor(Cursor.HAND);
				b.setBorder(new Border(
						new BorderStroke(Color.LIGHTGRAY, BorderStrokeStyle.SOLID, new CornerRadii(3), new BorderWidths(2), new Insets(1))));

			}
		});

		b.setOnMouseExited(new EventHandler<Event>() {

			@Override
			public void handle(Event arg0) {
				b.setCursor(Cursor.DEFAULT);
				b.setBorder(
						new Border(new BorderStroke(Color.BLACK, BorderStrokeStyle.SOLID, new CornerRadii(3), new BorderWidths(2), new Insets(1))));

			}
		});
	}
}
