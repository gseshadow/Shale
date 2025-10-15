module IntakeGenerator {
	requires javafx.base;
	requires transitive javafx.graphics;
	requires javafx.controls;
	requires javafx.fxml;
	requires poi;
	requires poi.ooxml;
	requires java.desktop;
	requires com.hivemq.client.mqtt;
	requires java.sql;
	requires java.base;
	requires templ4docx;

	opens application to javafx.graphics, javafx.fxml, javafx.controls;
}
