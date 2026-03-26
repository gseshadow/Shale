package com.shale.ui.controller.support;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TextField;

public final class CaseListFilterSortSupport {

	public static final String SORT_NAME = "Name";
	public static final String SORT_INTAKE = "Date of Intake";
	public static final String SORT_SOL = "Statute of Limitations Date";
	public static final List<String> DEFAULT_SORT_OPTIONS = List.of(SORT_NAME, SORT_INTAKE, SORT_SOL);

	private CaseListFilterSortSupport() {
	}

	public static void initializeControls(
			TextField searchField,
			ChoiceBox<String> sortChoice,
			Runnable onSelectionChanged) {
		if (sortChoice != null) {
			sortChoice.getItems().setAll(DEFAULT_SORT_OPTIONS);
			sortChoice.getSelectionModel().select(SORT_NAME);
			sortChoice.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> onSelectionChanged.run());
		}
		if (searchField != null) {
			searchField.textProperty().addListener((obs, oldV, newV) -> onSelectionChanged.run());
		}
	}

	public static void resetControls(TextField searchField, ChoiceBox<String> sortChoice) {
		if (searchField != null) {
			searchField.clear();
		}
		if (sortChoice != null) {
			sortChoice.getSelectionModel().select(SORT_NAME);
		}
	}

	public static String normalizedQuery(TextField searchField) {
		if (searchField == null) {
			return "";
		}
		String query = searchField.getText();
		return query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
	}

	public static boolean matchesQuery(String query, String... values) {
		if (query == null || query.isEmpty()) {
			return true;
		}
		for (String value : values) {
			if (safeText(value).toLowerCase(Locale.ROOT).contains(query)) {
				return true;
			}
		}
		return false;
	}

	public static <T> Comparator<T> comparator(
			ChoiceBox<String> sortChoice,
			Function<T, String> nameExtractor,
			Function<T, LocalDate> intakeDateExtractor,
			Function<T, LocalDate> solDateExtractor) {
		String sortOption = sortChoice == null ? SORT_NAME : sortChoice.getValue();
		if (SORT_SOL.equals(sortOption)) {
			return Comparator.comparing(solDateExtractor, CaseListFilterSortSupport::nullsLastDate);
		}
		if (SORT_INTAKE.equals(sortOption)) {
			return Comparator.comparing(intakeDateExtractor, CaseListFilterSortSupport::nullsLastDate).reversed();
		}
		return Comparator.comparing(row -> safeText(nameExtractor.apply(row)), String.CASE_INSENSITIVE_ORDER);
	}

	private static int nullsLastDate(LocalDate a, LocalDate b) {
		if (a == null && b == null) {
			return 0;
		}
		if (a == null) {
			return 1;
		}
		if (b == null) {
			return -1;
		}
		return a.compareTo(b);
	}

	private static String safeText(String text) {
		return text == null ? "" : text;
	}
}
