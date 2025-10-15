package GUIElements;

import javafx.geometry.Insets;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

public class ContentCard extends VBox {

	private String title;
	private Color color;

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public Color getColor() {
		return color;
	}

	public void setColor(Color color) {
		this.color = color;
	}

	public VBox getContent() {
		return content;
	}

	public void setContent(VBox content) {
		this.content = content;
	}

	private VBox content;

	public ContentCard(CardDetail contents) {
		setInsets(10);
		setGap(10);
		setPadding(10);
		title = contents.getTitle();
		color = contents.getColor();
		content = contents.getContent();

	}

	public void setWidth(int w) {
		this.setWidth(w);
	}

	public void setHeight(int h) {
		this.setHeight(h);
	}

	public void setInsets(int i) {
		this.setInsets(i);
	}

	public void setGap(int g) {
		this.setGap(g);
	}

	public void setPadding(int p) {
		this.setPadding(new Insets(p));
	}
}
