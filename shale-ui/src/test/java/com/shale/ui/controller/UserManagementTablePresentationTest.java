package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.shale.data.dao.UserDao;
import com.shale.ui.component.CommittedChangeTracker;
import com.shale.ui.component.UserCard;
import com.shale.ui.testutil.JavaFxTestSupport;

import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

final class UserManagementTablePresentationTest {
    @Test
    void readableColumnsAreResizableHaveMinimumsAndUseFlexibleConstrainedPolicy() {
        JavaFxTestSupport.runAndWait(() -> {
            UserManagementPane pane = pane();
            TableView<UserManagementPane.UserManagementViewRow> table = field(pane, "userManagementTable");
            List<TableColumn<UserManagementPane.UserManagementViewRow, ?>> columns = List.copyOf(table.getColumns());
            TableColumn<?, ?> name = columns.get(0), email = columns.get(1), initials = columns.get(2),
                    roles = columns.get(3), status = columns.get(4);

            assertSame(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN, table.getColumnResizePolicy(),
                    "the supported flexible policy must fill normal widths without undoing divider drags");
            for (TableColumn<?, ?> column : columns) {
                assertTrue(column.isResizable(), column.getText() + " must allow divider resizing");
                assertTrue(column.getMinWidth() > 0, column.getText() + " must retain a usable minimum");
            }
            assertAll(
                    () -> assertTrue(name.getPrefWidth() > initials.getPrefWidth()),
                    () -> assertTrue(name.getPrefWidth() > status.getPrefWidth()),
                    () -> assertTrue(email.getPrefWidth() > initials.getPrefWidth()),
                    () -> assertTrue(email.getPrefWidth() > status.getPrefWidth()),
                    () -> assertTrue(roles.getPrefWidth() > status.getPrefWidth()),
                    () -> assertEquals(854, columns.stream().mapToDouble(TableColumn::getPrefWidth).sum(), 0.01,
                            "the preferred widths should cleanly consume the normal popup table width"));
        });
    }

    @Test
    void nameCellUsesAFluidMiniCardWithFullNameTooltipAndClearsWhenRecycled() {
        JavaFxTestSupport.runAndWait(() -> {
            var cell = new UserManagementPane.UserNameCell(new com.shale.ui.component.factory.UserCardFactory(null));
            var row = row(2, "Alexandria Very-Long-Surname", "alexandria@example.test", true, true);
            cell.resize(310, 36);
            cell.updateItem(row, false);

            UserCard card = assertInstanceOf(UserCard.class, cell.getGraphic());
            Label name = (Label) card.lookup("#user-card-name-label");
            assertAll(
                    () -> assertEquals(290, card.getPrefWidth(), 0.01, "the card must track the cell's available width"),
                    () -> assertEquals(0, card.getMinWidth()),
                    () -> assertEquals(Double.MAX_VALUE, card.getMaxWidth()),
                    () -> assertEquals(0, name.getMinWidth()),
                    () -> assertEquals(Double.MAX_VALUE, name.getMaxWidth()),
                    () -> assertEquals("Alexandria Very-Long-Surname", name.getText()),
                    () -> assertEquals("Alexandria Very-Long-Surname", cell.getTooltip().getText()),
                    () -> assertEquals("Alexandria Very-Long-Surname", name.getTooltip().getText()));

            cell.updateItem(null, true);
            assertAll(() -> assertNull(cell.getGraphic()), () -> assertNull(cell.getText()),
                    () -> assertNull(cell.getTooltip()), () -> assertTrue(cell.getStyle().isEmpty()));
        });
    }

    @Test
    void emailAndRoleCellsExposeFullValuesAndClearRecycledState() {
        JavaFxTestSupport.runAndWait(() -> {
            for (String value : List.of("alexandria.long.login@example.test", "Administrator, Attorney")) {
                var cell = new UserManagementPane.FullValueTextCell();
                cell.updateItem(value, false);
                assertEquals(value, cell.getText());
                assertEquals(value, cell.getTooltip().getText());
                cell.updateItem(null, true);
                assertAll(() -> assertNull(cell.getText()), () -> assertNull(cell.getGraphic()),
                        () -> assertNull(cell.getTooltip()), () -> assertTrue(cell.getStyle().isEmpty()));
            }
        });
    }

    @Test
    void configuredEmailColumnStillSortsByAuthoritativeValue() {
        JavaFxTestSupport.runAndWait(() -> {
            UserManagementPane pane = pane();
            TableView<UserManagementPane.UserManagementViewRow> table = field(pane, "userManagementTable");
            @SuppressWarnings("unchecked")
            TableColumn<UserManagementPane.UserManagementViewRow, String> email =
                    (TableColumn<UserManagementPane.UserManagementViewRow, String>) table.getColumns().get(1);
            table.getItems().setAll(row(1, "Zed", "zed@example.test", false, false),
                    row(2, "Amy", "amy@example.test", true, false));
            email.setSortType(TableColumn.SortType.ASCENDING);
            table.getSortOrder().setAll(email);
            table.sort();
            assertEquals(List.of("amy@example.test", "zed@example.test"),
                    table.getItems().stream().map(UserManagementPane.UserManagementViewRow::email).toList());
        });
    }

    private static UserManagementPane pane() {
        UserDao dao = new UserDao(() -> { throw new AssertionError("presentation tests must not load users"); });
        return new UserManagementPane(dao, Runnable::run, new CommittedChangeTracker(), 7, 11);
    }

    private static UserManagementPane.UserManagementViewRow row(int id, String name, String email,
            boolean admin, boolean attorney) {
        return new UserManagementPane.UserManagementViewRow(new UserDao.UserManagementRow(id, name, "User", name,
                email, "", "#336699", "AU", attorney, admin, false, false, new byte[] { 1 }));
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Object owner, String name) {
        try {
            Field field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return (T) field.get(owner);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
