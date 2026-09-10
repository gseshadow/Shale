package com.shale.ui.component;

import java.util.List;
import java.util.Objects;

import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.paint.Color;

/** Domain-neutral, presentation-only group for database-colored classifications. */
public class ClassificationChipGroup extends FlowPane {
    public enum Size { COMPACT, STANDARD }
    public record Chip(String label, String color, String category, long definitionId, boolean primary) { }
    private static final Color FALLBACK = Color.web("#6C757D");

    public ClassificationChipGroup(List<Chip> values, Size size) {
        Objects.requireNonNull(values, "values"); Objects.requireNonNull(size, "size");
        getStyleClass().addAll("contact-classification-chip-group",
                size == Size.COMPACT ? "contact-classification-chip-group-compact" : "contact-classification-chip-group-standard");
        setHgap(size == Size.COMPACT ? 5 : 6); setVgap(size == Size.COMPACT ? 4 : 6);
        values.forEach(value -> getChildren().add(chip(value, size)));
        setVisible(!values.isEmpty()); setManaged(!values.isEmpty());
    }

    private static Label chip(Chip value, Size size) {
        String text = value.label() == null || value.label().isBlank() ? "Unknown" : value.label().trim();
        Label label = new Label(value.primary() ? text + " · Primary" : text);
        label.getStyleClass().addAll("contact-classification-chip",
                size == Size.COMPACT ? "contact-classification-chip-compact" : "contact-classification-chip-standard");
        if (value.primary()) label.getStyleClass().add("classification-chip-primary");
        Color color = parse(value.color());
        String rgb = hex(color), foreground = luminance(color) > .52 ? "#112542" : "#FFFFFF";
        label.setStyle("-fx-background-color: " + rgba(color, size == Size.COMPACT ? .16 : .20)
                + "; -fx-border-color: " + rgb + "; -fx-text-fill: " + foreground + ";");
        String category = value.category() == null || value.category().isBlank() ? "Classification" : value.category();
        label.setAccessibleText(category + ": " + text + (value.primary() ? ", primary" : ""));
        label.setTooltip(new Tooltip(category + (value.primary() ? " · Primary" : "")));
        label.getProperties().put("classificationDefinitionId", value.definitionId());
        label.getProperties().put("classificationPrimary", value.primary());
        return label;
    }
    private static Color parse(String value){try{return value!=null&&value.matches("#[0-9A-Fa-f]{6}")?Color.web(value):FALLBACK;}catch(IllegalArgumentException ignored){return FALLBACK;}}
    private static double luminance(Color c){return .2126*c.getRed()+.7152*c.getGreen()+.0722*c.getBlue();}
    private static String hex(Color c){return String.format("#%02X%02X%02X",Math.round(c.getRed()*255),Math.round(c.getGreen()*255),Math.round(c.getBlue()*255));}
    private static String rgba(Color c,double alpha){return String.format("rgba(%d,%d,%d,%.2f)",Math.round(c.getRed()*255),Math.round(c.getGreen()*255),Math.round(c.getBlue()*255),alpha);}
}
