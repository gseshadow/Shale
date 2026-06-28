package com.shale.ui.util;

import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

/**
 * Shared table setup for Settings lookup-administration lists.
 *
 * <p>The Settings screen owns lookup CRUD behavior and FXML layout. This helper
 * only centralizes repeated lookup table column wiring and neutral metadata-chip
 * cells so table structure, selection, and service behavior stay unchanged.</p>
 */
public final class LookupAdministrationTableFactory {

	private LookupAdministrationTableFactory() {
	}

	public static <S> void configureCaseStatusTable(
			TableView<S> table,
			TableColumn<S, String> nameColumn,
			TableColumn<S, String> closedColumn,
			TableColumn<S, Integer> sortOrderColumn,
			TableColumn<S, String> lifecycleKeyColumn,
			TableColumn<S, String> systemKeyColumn) {
		if (table == null) {
			return;
		}
		setValueFactory(nameColumn, "name");
		setValueFactory(closedColumn, "closedState");
		setValueFactory(sortOrderColumn, "sortOrder");
		setValueFactory(lifecycleKeyColumn, "lifecycleKey");
		setValueFactory(systemKeyColumn, "systemKey");
		setMetadataChipCell(closedColumn);
		setMetadataChipCell(sortOrderColumn);
		setMetadataChipCell(lifecycleKeyColumn);
		setMetadataChipCell(systemKeyColumn);
	}

	public static <S> void configurePracticeAreaTable(
			TableView<S> table,
			TableColumn<S, String> nameColumn,
			TableColumn<S, String> colorColumn,
			TableColumn<S, String> activeColumn,
			TableColumn<S, String> systemKeyColumn) {
		if (table == null) {
			return;
		}
		setValueFactory(nameColumn, "name");
		setValueFactory(colorColumn, "color");
		setValueFactory(activeColumn, "activeState");
		setValueFactory(systemKeyColumn, "systemKey");
		setMetadataChipCell(colorColumn);
		setMetadataChipCell(activeColumn);
		setMetadataChipCell(systemKeyColumn);
	}

	private static <S, T> void setValueFactory(TableColumn<S, T> column, String propertyName) {
		if (column != null) {
			column.setCellValueFactory(new PropertyValueFactory<>(propertyName));
		}
	}

	public static <S, T> void setMetadataChipCell(TableColumn<S, T> column) {
		if (column != null) {
			column.setCellFactory(ignored -> metadataChipCell());
		}
	}

	public static <S, T> TableCell<S, T> metadataChipCell() {
		return new TableCell<>() {
			@Override
			protected void updateItem(T item, boolean empty) {
				super.updateItem(item, empty);
				if (empty) {
					setText(null);
					setGraphic(null);
					return;
				}
				String text = item == null ? null : String.valueOf(item);
				setText(null);
				setGraphic(MetadataChipFactory.compact(text, text));
			}
		};
	}
}
