package com.shale.ui.controller.support;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;

public final class CaseListUiSupport {

	private CaseListUiSupport() {
	}

	public static final int STATUS_POTENTIAL = 6;
	public static final int STATUS_ACTIVE = 7;
	public static final int STATUS_MEDIATION = 8;
	public static final int STATUS_TRIAL = 9;
	public static final int STATUS_SETTLEMENT = 10;
	public static final int STATUS_DENIED = 11;
	public static final int STATUS_CLOSED = 12;
	public static final int STATUS_ACCEPTED = 14;

	public static final Map<Integer, String> STATUS_FILTER_OPTIONS = buildStatusOptions();

	private static Map<Integer, String> buildStatusOptions() {
		Map<Integer, String> options = new LinkedHashMap<>();
		options.put(STATUS_POTENTIAL, "Potential");
		options.put(STATUS_ACTIVE, "Active");
		options.put(STATUS_MEDIATION, "Mediation");
		options.put(STATUS_TRIAL, "Trial");
		options.put(STATUS_SETTLEMENT, "Settlement");
		options.put(STATUS_DENIED, "Denied");
		options.put(STATUS_CLOSED, "Closed");
		options.put(STATUS_ACCEPTED, "Accepted");
		return options;
	}

	public static Set<Integer> defaultSelectedStatuses() {
		return new LinkedHashSet<>(STATUS_FILTER_OPTIONS.keySet());
	}

	public static void initializeStatusFilterMenu(MenuButton statusFilterMenuButton,
			Set<Integer> selectedStatusIds,
			Runnable onSelectionChanged) {
		if (statusFilterMenuButton == null) {
			return;
		}

		statusFilterMenuButton.getItems().clear();

		MenuItem selectAll = new MenuItem("Select All");
		selectAll.setOnAction(event -> {
			selectedStatusIds.clear();
			selectedStatusIds.addAll(STATUS_FILTER_OPTIONS.keySet());
			updateStatusMenuItemsFromSelection(statusFilterMenuButton, selectedStatusIds);
			onSelectionChanged.run();
		});

		MenuItem clearAll = new MenuItem("Clear All");
		clearAll.setOnAction(event -> {
			selectedStatusIds.clear();
			updateStatusMenuItemsFromSelection(statusFilterMenuButton, selectedStatusIds);
			onSelectionChanged.run();
		});

		statusFilterMenuButton.getItems().addAll(selectAll, clearAll, new SeparatorMenuItem());

		for (Map.Entry<Integer, String> entry : STATUS_FILTER_OPTIONS.entrySet()) {
			Integer statusId = entry.getKey();
			CheckMenuItem item = new CheckMenuItem(entry.getValue());
			item.setSelected(selectedStatusIds.contains(statusId));
			item.setOnAction(event -> {
				if (item.isSelected()) {
					selectedStatusIds.add(statusId);
				} else {
					selectedStatusIds.remove(statusId);
				}
				updateStatusMenuButtonText(statusFilterMenuButton, selectedStatusIds);
				onSelectionChanged.run();
			});
			statusFilterMenuButton.getItems().add(item);
		}

		updateStatusMenuButtonText(statusFilterMenuButton, selectedStatusIds);
	}

	private static void updateStatusMenuItemsFromSelection(MenuButton statusFilterMenuButton, Set<Integer> selectedStatusIds) {
		for (MenuItem item : statusFilterMenuButton.getItems()) {
			if (item instanceof CheckMenuItem checkItem) {
				Integer statusId = statusIdForLabel(checkItem.getText());
				if (statusId != null) {
					checkItem.setSelected(selectedStatusIds.contains(statusId));
				}
			}
		}
		updateStatusMenuButtonText(statusFilterMenuButton, selectedStatusIds);
	}

	private static Integer statusIdForLabel(String label) {
		for (Map.Entry<Integer, String> entry : STATUS_FILTER_OPTIONS.entrySet()) {
			if (entry.getValue().equals(label)) {
				return entry.getKey();
			}
		}
		return null;
	}

	private static void updateStatusMenuButtonText(MenuButton statusFilterMenuButton, Set<Integer> selectedStatusIds) {
		statusFilterMenuButton
				.setText("Status (" + selectedStatusIds.size() + "/" + STATUS_FILTER_OPTIONS.size() + ")");
	}
}
