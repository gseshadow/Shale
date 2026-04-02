package com.shale.ui.controller.support;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;

public final class CaseListUiSupport {

	private CaseListUiSupport() {
	}

	public record StatusFilterOption(int id, String label, boolean isClosed) {
	}

	public static Set<Integer> defaultSelectedStatuses(List<StatusFilterOption> options) {
		Set<Integer> defaults = new LinkedHashSet<>();
		if (options == null) {
			return defaults;
		}
		for (StatusFilterOption option : options) {
			if (option == null || option.isClosed()) {
				continue;
			}
			defaults.add(option.id());
		}
		return defaults;
	}

	public static void initializeStatusFilterMenu(MenuButton statusFilterMenuButton,
			Set<Integer> selectedStatusIds,
			List<StatusFilterOption> options,
			Runnable onSelectionChanged) {
		if (statusFilterMenuButton == null) {
			return;
		}

		statusFilterMenuButton.getItems().clear();

		MenuItem selectAll = new MenuItem("Select All");
		selectAll.setOnAction(event -> {
			selectedStatusIds.clear();
			for (StatusFilterOption option : options) {
				if (option != null) {
					selectedStatusIds.add(option.id());
				}
			}
			updateStatusMenuItemsFromSelection(statusFilterMenuButton, selectedStatusIds, options);
			onSelectionChanged.run();
		});

		MenuItem clearAll = new MenuItem("Clear All");
		clearAll.setOnAction(event -> {
			selectedStatusIds.clear();
			updateStatusMenuItemsFromSelection(statusFilterMenuButton, selectedStatusIds, options);
			onSelectionChanged.run();
		});

		statusFilterMenuButton.getItems().addAll(selectAll, clearAll, new SeparatorMenuItem());

		for (StatusFilterOption option : options) {
			if (option == null) {
				continue;
			}
			Integer statusId = option.id();
			CheckMenuItem item = new CheckMenuItem(option.label());
			item.setUserData(statusId);
			item.setSelected(selectedStatusIds.contains(statusId));
			item.setOnAction(event -> {
				if (item.isSelected()) {
					selectedStatusIds.add(statusId);
				} else {
					selectedStatusIds.remove(statusId);
				}
				updateStatusMenuButtonText(statusFilterMenuButton, selectedStatusIds, options);
				onSelectionChanged.run();
			});
			statusFilterMenuButton.getItems().add(item);
		}

		updateStatusMenuButtonText(statusFilterMenuButton, selectedStatusIds, options);
	}

	private static void updateStatusMenuItemsFromSelection(MenuButton statusFilterMenuButton,
			Set<Integer> selectedStatusIds,
			List<StatusFilterOption> options) {
		for (MenuItem item : statusFilterMenuButton.getItems()) {
			if (item instanceof CheckMenuItem checkItem) {
				Object userData = checkItem.getUserData();
				Integer statusId = userData instanceof Integer ? (Integer) userData : null;
				if (statusId != null) {
					checkItem.setSelected(selectedStatusIds.contains(statusId));
				}
			}
		}
		updateStatusMenuButtonText(statusFilterMenuButton, selectedStatusIds, options);
	}

	private static void updateStatusMenuButtonText(MenuButton statusFilterMenuButton,
			Set<Integer> selectedStatusIds,
			List<StatusFilterOption> options) {
		int total = options == null ? 0 : options.size();
		statusFilterMenuButton
				.setText("Status (" + selectedStatusIds.size() + "/" + total + ")");
	}
}
