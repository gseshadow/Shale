package com.shale.ui.controller;

import com.shale.core.dto.*;
import com.shale.core.service.CaseServicePort;
import com.shale.ui.controller.support.CaseUpdatesWorkspaceController;
import com.shale.ui.state.AppState;
import com.shale.ui.testutil.JavaFxTestSupport;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.junit.jupiter.api.Test;

import java.lang.reflect.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises shared card actions, real destructive confirmation, persistence, and both entry points. */
class CaseUpdateDeletionTest {
    private static final byte[] VERSION = {0,0,0,0,0,0,0,1};
    private static CaseUpdateDto update(int creator, boolean deleted) {
        return new CaseUpdateDto(90,501,"original",LocalDateTime.of(2026,10,20,12,0),null,creator,"Author",VERSION,deleted);
    }

    @Test void sharedCardsOfferDeleteOnlyForCreatorOrAdminAndNeverForDeletedRecords() {
        JavaFxTestSupport.runAndWait(() -> {
            for (boolean admin : List.of(false,true)) for (int creator : List.of(17,31)) for (boolean deleted : List.of(false,true)) {
                Fixture f = new Fixture(); f.state.setAdmin(admin);
                Node card = (Node)invoke(f.controller,"createCaseUpdateCardInternal",new Class<?>[]{CaseUpdateDto.class},update(creator,deleted));
                List<Button> actions = descendants(card).stream().filter(Button.class::isInstance).map(Button.class::cast).toList();
                assertEquals(!deleted && (admin || creator == 31), actions.stream().anyMatch(b -> b.getText().equals("Delete")),
                        "Both rail and workspace use this shared card authorization");
                actions.stream().filter(b -> b.getText().equals("Delete")).forEach(b -> assertTrue(b.getStyleClass().contains("shale-control-danger")));
            }
        });
    }

    @Test void explicitConfirmationCancellationAndSuccessfulDeleteRefreshBothSurfacesKeepingFilters() {
        Fixture[] fixture = new Fixture[1];
        JavaFxTestSupport.runAndWait(() -> {
            Fixture f = new Fixture(); fixture[0] = f;
            var workspace = new CaseUpdatesWorkspaceController(501,f.service,f.state,Runnable::run,Runnable::run,
                    dto -> new Label(dto.getNoteText()), () -> {});
            set(f.controller,"updatesWorkspace",workspace); workspace.activate();
            field(workspace,"search",TextField.class).setText("original");
            workspace.refresh();
            Button delete = deleteButton(f);
            Platform.runLater(() -> confirmationButton("Cancel").fire());
            delete.fire(); assertEquals(0,f.deletes,"Cancel must not invoke persistence");
            assertEquals(1,field(workspace,"rows",VBox.class).getChildren().size());
            Platform.runLater(() -> {
                var dialog = confirmationRoot();
                String copy = descendants(dialog).stream().filter(Label.class::isInstance).map(Label.class::cast).map(Label::getText).reduce("",(a,b)->a+" "+b);
                assertTrue(copy.contains("removed from normal case history") && copy.contains("Author"),"Confirmation must identify the action and consequence");
                assertFalse(confirmationButton("Delete Update").isDefaultButton(),"Enter must not delete by default");
                confirmationButton("Delete Update").fire();
            });
            delete.fire(); assertEquals(1,f.deletes);
        });
        JavaFxTestSupport.runAndWait(() -> {
            Fixture f = fixture[0];
            var workspace = field(f.controller,"updatesWorkspace",CaseUpdatesWorkspaceController.class);
            assertTrue(field(workspace,"rows",VBox.class).getChildren().isEmpty(),"Committed deletion must leave active results");
            assertEquals("original",field(workspace,"search",TextField.class).getText());
            assertEquals("No updates match these filters.",field(workspace,"status",Label.class).getText());
            assertTrue(f.pageSizes.containsAll(List.of(25,50)),"Deletion must invalidate both rail and workspace");
            assertEquals(501,f.command.caseId()); assertEquals(41,f.command.shaleClientId()); assertEquals(31,f.command.actorUserId());
            assertArrayEquals(VERSION,f.command.expectedRowVersion());
        });
    }

    @Test void pendingOrRejectedDeletionNeverOptimisticallyRemovesResults() {
        JavaFxTestSupport.runAndWait(() -> {
            Fixture f = new Fixture();
            List<Runnable> queued = new ArrayList<>(); set(f.controller,"caseUpdateExecutor",new DirectExecutor(queued::add));
            VBox feed = new VBox(new Label("original")); set(f.controller,"caseUpdatesFeedBox",feed);
            f.success = false;
            @SuppressWarnings("unchecked") var result = (CompletableFuture<String>)invoke(f.controller,"persistCaseUpdateDeletion",new Class<?>[]{CaseUpdateDto.class},update(31,false));
            assertFalse(result.isDone()); assertEquals(0,f.deletes); assertEquals(1,feed.getChildren().size());
            queued.removeFirst().run(); assertTrue(result.isDone()); assertNotNull(result.join());
            assertEquals(1,feed.getChildren().size(),"A stale, denied or unavailable mutation cannot remove the displayed update");
        });
    }

    private static Button deleteButton(Fixture f) {
        Node card = (Node)invoke(f.controller,"createCaseUpdateCardInternal",new Class<?>[]{CaseUpdateDto.class},update(31,false));
        return descendants(card).stream().filter(Button.class::isInstance).map(Button.class::cast).filter(b -> b.getText().equals("Delete")).findFirst().orElseThrow();
    }
    private static Parent confirmationRoot() {
        return Window.getWindows().stream().filter(Window::isShowing).map(Window::getScene).filter(Objects::nonNull)
                .map(javafx.scene.Scene::getRoot).filter(r -> descendants(r).stream().anyMatch(n -> n instanceof Button b && b.getText().equals("Delete Update")))
                .findFirst().orElseThrow();
    }
    private static Button confirmationButton(String text) {
        return descendants(confirmationRoot()).stream().filter(Button.class::isInstance).map(Button.class::cast).filter(b -> b.getText().equals(text)).findFirst().orElseThrow();
    }
    private static List<Node> descendants(Node root) {
        List<Node> nodes = new ArrayList<>(); nodes.add(root);
        if (root instanceof Parent parent) for (Node child : parent.getChildrenUnmodifiable()) nodes.addAll(descendants(child));
        return nodes;
    }
    private static final class Fixture {
        final CaseController controller = new CaseController(); final AppState state = new AppState();
        int deletes; boolean success = true; boolean removed; CaseServicePort.DeleteCaseUpdateCommand command;
        final List<Integer> pageSizes = new ArrayList<>(); final CaseServicePort service;
        Fixture() {
            state.setUserId(31); state.setShaleClientId(41);
            service = (CaseServicePort)Proxy.newProxyInstance(CaseServicePort.class.getClassLoader(),new Class<?>[]{CaseServicePort.class},(p,m,a)->switch(m.getName()) {
                case "deleteCaseUpdate" -> { deletes++; command=(CaseServicePort.DeleteCaseUpdateCommand)a[0]; removed=success; yield success; }
                case "searchCaseUpdates" -> { pageSizes.add(((CaseUpdateQuery)a[0]).pageSize()); yield new CaseUpdatePage(removed?List.of():List.of(update(31,false)),null); }
                case "listCaseUpdateAuthors" -> List.of(new CaseUpdateAuthorDto(31,"Author",null));
                default -> null;
            });
            set(controller,"caseId",501); set(controller,"appState",state); set(controller,"caseService",service);
            set(controller,"caseUpdateExecutor",new DirectExecutor(Runnable::run));
        }
    }
    private static <T> T field(Object target,String name,Class<T> type) {
        try { Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return type.cast(f.get(target)); } catch(Exception ex){throw new AssertionError(ex);}
    }
    private static void set(Object target,String name,Object value) {
        try { Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value); } catch(Exception ex){throw new AssertionError(ex);}
    }
    private static Object invoke(Object target,String name,Class<?>[] types,Object...args) {
        try { Method m=target.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(target,args); } catch(Exception ex){throw new AssertionError(ex);}
    }
    private static final class DirectExecutor extends AbstractExecutorService {
        private final java.util.function.Consumer<Runnable> execute;
        DirectExecutor(java.util.function.Consumer<Runnable> execute) { this.execute=execute; }
        public void shutdown(){} public List<Runnable> shutdownNow(){return List.of();} public boolean isShutdown(){return false;}
        public boolean isTerminated(){return false;} public boolean awaitTermination(long time,TimeUnit unit){return true;}
        public void execute(Runnable job){execute.accept(job);}
    }
}
