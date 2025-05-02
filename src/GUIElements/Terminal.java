package GUIElements;

import javafx.geometry.Insets;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.FlowPane;

public class Terminal extends FlowPane {
	TextArea output = new TextArea();

	public Terminal() {
		ScrollPane scroll = new ScrollPane();
		scroll.setPadding(new Insets(5));
		scroll.autosize();

		scroll.setContent(output);
		this.getChildren().add(scroll);

	}

	public void sendMessage(String text) {
		output.setText(output.getText() + text);

	}

	/*
	 * Use the following to write messages to terminal
	 * Main.sendTerminalMessage("Your Message");// TODO
	 */

}
