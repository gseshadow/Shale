module IntakeGenerator {
	requires javafx.controls;
	requires javafx.fxml;
	requires javafx.graphics;
	requires org.docx4j.core;

	opens application to javafx.graphics, javafx.fxml;
}
