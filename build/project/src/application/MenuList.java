package application;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;

public class MenuList extends VBox {

	private Button newIntake = new Button("New Intake");
	private Button potentials = new Button("View Potentials");
	private Button back = new Button("Back");

	public MenuList(boolean showBack) {
		this.setSpacing(10);
		this.setPadding(new Insets(10));
		this.setAlignment(Pos.TOP_LEFT);

		newIntake.setPrefSize(200, 50);
		this.getChildren().add(newIntake);

		potentials.setPrefSize(200, 50);
		this.getChildren().add(potentials);

		back.setPrefSize(200, 50);

		back.setVisible(showBack);
		newIntake.setVisible(!showBack);
		potentials.setVisible(!showBack);

		this.getChildren().add(back);

		newIntake.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				newIntake.setVisible(false);
				potentials.setVisible(false);
				back.setVisible(true);

				Main.setPotential(new Potential());

				Intake temp = new Intake(new Potential());
				Main.setIntake(temp);
				Main.getController().changeTop(new TopBar(0, temp));
				Main.getController().changeCenter(temp);

			}
		});

		potentials.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				Main.getController().changeTop(null);
				Main.getController().changeCenter(new PotentialsList());
			}
		});

		back.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				/* create popup menu here before changing the visibility of the other buttons */
				back.setVisible(false);
				newIntake.setVisible(true);
				potentials.setVisible(true);

				Main.getController().changeTop(null);
				Main.getController().changeCenter(new PotentialsList());

			}
		});

	}

}
