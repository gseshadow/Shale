package GUIElements;

import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

public class CardDetail {

	private String title = "";
	private Color color = new Color(0, 0, 0, 0);
	private VBox content;

	/*
	 * THIS ISNT REALLY CORRECT AND IS UNUSED CONTENT CARD IS BASICALLY THE SAME CLASS AND ALSO DOESN'T DO ANYTHING
	 */
	public CardDetail(String title) {
		this.title = title;
		if (content == null)
			buildContent();
	}

	public CardDetail(String title, Color color) {
		this.title = title;
		this.color = color;
	}

	public CardDetail(String title, Color color, VBox content) {
		this.title = title;
		this.color = color;
		this.content = content;
	}

	private void buildContent() {
		content = new VBox();
		

	}

	public VBox getContent() {
		return content;
	}

	public Color getColor() {
		return color;
	}

	public String getTitle() {
		return title;
	}

}
