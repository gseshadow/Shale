package com.shale.ui.controller;

import com.shale.core.dto.*;
import com.shale.core.service.CaseServicePort;
import com.shale.ui.component.UserSelectionField;
import com.shale.ui.controller.support.CaseUpdatesWorkspaceController;
import com.shale.ui.state.AppState;
import com.shale.ui.testutil.JavaFxTestSupport;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.Test;

import java.lang.reflect.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class CaseUpdatesWorkspaceTest {
    private static final LocalDateTime AT = LocalDateTime.of(2026,10,20,12,0);
    private static final byte[] VERSION = {0,0,0,0,0,0,0,1};

    @Test void filtersComposeAndResetToNewestActiveFirstPage() {
        JavaFxTestSupport.runAndWait(() -> {
            Fixture f = new Fixture(true); f.workspace.activate();
            assertEquals(CaseUpdateQuery.Sort.NEWEST, f.last().sort());
            assertEquals(50, f.last().pageSize());
            field(f.workspace,"search",TextField.class).setText("records");
            field(f.workspace,"from",DatePicker.class).setValue(LocalDate.of(2026,10,1));
            field(f.workspace,"through",DatePicker.class).setValue(LocalDate.of(2026,10,20));
            combo(f.workspace,"sort").setValue(CaseUpdateQuery.Sort.EDITED);
            combo(f.workspace,"visibility").setValue(CaseUpdateQuery.Visibility.DELETED);
            @SuppressWarnings("unchecked") UserSelectionField<CaseUpdateAuthorDto> authors = field(f.workspace,"author",UserSelectionField.class);
            authors.setSelectedUser(new CaseUpdateAuthorDto(17,"Same Name",null));
            assertEquals("records",f.last().text()); assertEquals(17,f.last().authorUserId());
            assertEquals(LocalDate.of(2026,10,1),f.last().from()); assertEquals(LocalDate.of(2026,10,20),f.last().through());
            assertEquals(CaseUpdateQuery.Sort.EDITED,f.last().sort());
            assertEquals(CaseUpdateQuery.Visibility.DELETED,f.last().visibility());
            f.workspace.resetFilters();
            assertEquals(CaseUpdateQuery.newest(501,41,31,"",50).inZone(java.time.ZoneId.systemDefault()), f.last(), "Clear must reset every filter and cursor");
            int count=f.queries.size();
            field(f.workspace,"from",DatePicker.class).setValue(LocalDate.of(2026,10,21));
            field(f.workspace,"through",DatePicker.class).setValue(LocalDate.of(2026,10,20));
            assertEquals(count+1,f.queries.size(), "An invalid date range must never reach the service");
            assertTrue(field(f.workspace,"status",Label.class).getText().contains("Check the date range"));
        });
    }

    @Test void pagingIsBoundedAndFilterChangesRestartAtFirstPage() {
        JavaFxTestSupport.runAndWait(() -> {
            Fixture f=new Fixture(false); f.workspace.activate();
            var cursor=new CaseUpdateQuery.Cursor(AT,90);
            f.page=new CaseUpdatePage(List.of(update(90,"first")),cursor); f.workspace.refresh();
            field(f.workspace,"next",Button.class).fire();
            assertEquals(cursor,f.last().after()); assertEquals(50,f.last().pageSize());
            field(f.workspace,"previous",Button.class).fire(); assertNull(f.last().after());
            combo(f.workspace,"sort").setValue(CaseUpdateQuery.Sort.OLDEST); assertNull(f.last().after());
            assertFalse(f.workspace.view().getChildren().stream().anyMatch(n -> n == field(f.workspace,"visibility",ComboBox.class)),
                    "Ordinary users must not receive a deleted-record selector");
            assertEquals(CaseUpdateQuery.Visibility.ACTIVE,f.last().visibility());
        });
    }

    @Test void newerRequestsNavigationAndIdentityChangesRejectLateResults() {
        JavaFxTestSupport.runAndWait(() -> {
            Fixture f=new Fixture(false); List<Runnable> queued=new ArrayList<>(); f.dispatch=queued::add;
            f.workspace.activate(); // authors + page are queued
            f.page=new CaseUpdatePage(List.of(update(91,"new")),null);
            f.workspace.refresh();
            queued.getLast().run();
            for(int i=queued.size()-2;i>=0;i--) queued.get(i).run();
            VBox rows=field(f.workspace,"rows",VBox.class);
            assertEquals("new",((Label)rows.getChildren().getFirst()).getText(),"Old completion must not replace a newer page");
            queued.clear(); f.page=new CaseUpdatePage(List.of(update(92,"hidden")),null); f.workspace.refresh();
            f.workspace.deactivate(); queued.forEach(Runnable::run);
            assertTrue(rows.getChildren().isEmpty(),"Detached workspace must reject late reads");
            queued.clear(); f.workspace.activate(); f.state.setShaleClientId(42); queued.forEach(Runnable::run);
            assertTrue(rows.getChildren().isEmpty(),"Another tenant's session must never display a prior response");
        });
    }

    @Test void emptyFilteredAndFailureStatesAreDistinctAndRefreshRecovers() {
        JavaFxTestSupport.runAndWait(() -> {
            Fixture f=new Fixture(false); f.workspace.activate();
            Label status=field(f.workspace,"status",Label.class);
            assertTrue(status.getText().startsWith("No updates yet"));
            field(f.workspace,"from",DatePicker.class).setValue(LocalDate.of(2026,1,1));
            assertEquals("No updates match these filters.",status.getText());
            f.fail=true; f.workspace.refresh(); assertTrue(status.getText().contains("could not be loaded"));
            f.fail=false; f.page=new CaseUpdatePage(List.of(update(90,"recovered")),null); f.workspace.refresh();
            assertEquals("1 updates on this page",status.getText());
        });
    }

    @Test void authorLoadFailureIsIndependentFromPageStateAndRefreshRetriesIt() {
        JavaFxTestSupport.runAndWait(() -> {
            Fixture f = new Fixture(false); f.failAuthors = true; f.workspace.activate();
            Label authors = field(f.workspace, "authorStatus", Label.class);
            assertTrue(authors.isVisible(), "Successful note loading must not erase a failed author lookup");
            assertTrue(authors.getText().contains("Refresh to retry"));
            assertTrue(field(f.workspace, "status", Label.class).getText().startsWith("No updates yet"));
            f.failAuthors = false; f.workspace.refresh();
            assertFalse(authors.isVisible());
            assertTrue(field(f.workspace, "authorsLoaded", Boolean.class));
        });
    }

    @Test void viewAllNavigatesAndSharedMutationRefreshesRailAndTabWithoutReopening() {
        var current = new java.util.concurrent.atomic.AtomicReference<CaseController>();
        var fixture = new java.util.concurrent.atomic.AtomicReference<Fixture>();
        var loaded = new java.util.concurrent.atomic.AtomicReference<Parent>();
        JavaFxTestSupport.runAndWait(() -> {
            Fixture f=new Fixture(false); fixture.set(f);
            FXMLLoader loader=new FXMLLoader(getClass().getResource("/fxml/case.fxml"));
            Parent root=loader.load(); loaded.set(root);
            CaseController controller=loader.getController(); current.set(controller);
            set(controller,"caseId",501); set(controller,"caseService",f.service); set(controller,"appState",f.state);
            set(controller,"caseUpdateExecutor",new DirectExecutor());
            invoke(controller,"onCaseUpdatesChanged");
            assertEquals(25,f.last().pageSize());
            field(controller,"viewAllCaseUpdatesButton",Button.class).fire();
            assertEquals("Case Updates",field(controller,"activeSectionName",String.class));
            assertTrue(field(controller,"caseUpdatesTabPane",VBox.class).isVisible());
            assertNull(((javafx.scene.layout.BorderPane)root).getRight(),"Dedicated workspace should use the full center width");
        });
        JavaFxTestSupport.runAndWait(() -> {
            Fixture f=fixture.get(); f.page=new CaseUpdatePage(List.of(update(90,"saved")),null);
            @SuppressWarnings("unchecked") CompletableFuture<String> save=(CompletableFuture<String>) invoke(current.get(),"persistCaseUpdate",new Class<?>[]{CaseUpdateDto.class,String.class},null,"saved");
            assertTrue(save.isDone()); assertNull(save.join());
            assertEquals(1,f.creates); assertEquals(50,f.last().pageSize());
        });
        // Flush the production Platform.runLater completion before asserting visible synchronization.
        JavaFxTestSupport.runAndWait(() -> {
            CaseController controller=current.get(); Fixture f=fixture.get();
            CaseUpdatesWorkspaceController workspace=field(controller,"updatesWorkspace",CaseUpdatesWorkspaceController.class);
            assertFalse(field(workspace,"rows",VBox.class).getChildren().isEmpty(),"A committed save must refresh the visible tab");
            @SuppressWarnings("unchecked") Map<String,Button> tabs=field(controller,"sectionTabs",Map.class);
            tabs.get("Overview").fire();
            assertEquals(25,f.last().pageSize());
            assertFalse(field(controller,"caseUpdatesFeedBox",VBox.class).getChildren().isEmpty(),"Returning to the rail must reveal the saved update");
            assertSame(field(controller,"caseUpdatesPane",VBox.class),((javafx.scene.layout.BorderPane)loaded.get()).getRight());
        });
    }

    @Test void workspaceUsesSharedSemanticControlsAndBothThemes() {
        JavaFxTestSupport.runAndWait(() -> {
            Fixture f = new Fixture(true);
            f.page = new CaseUpdatePage(List.of(update(90, "Called the client to confirm the records request.\nFollow up with the provider next week."),
                    update(89, "Reviewed the case documents and recorded the next steps.")), null);
            f.workspace.activate();
            CaseController cards = new CaseController(); set(cards, "appState", f.state);
            VBox rows = field(f.workspace, "rows", VBox.class);
            rows.getChildren().clear();
            for (CaseUpdateDto dto : f.page.items()) rows.getChildren().add((Node) invoke(cards, "createCaseUpdateCardInternal", new Class<?>[]{CaseUpdateDto.class}, dto));
            var scene = new javafx.scene.Scene(new javafx.scene.layout.StackPane(f.workspace.view()), 1180, 760);
            var manager = new com.shale.ui.theme.ThemeManager();
            manager.register(scene);
            for (var theme : com.shale.ui.theme.Theme.values()) {
                manager.setActiveTheme(theme);
                f.workspace.view().applyCss(); f.workspace.view().layout();
                for (Node node : f.workspace.view().lookupAll(".button")) {
                    if (!(node instanceof Button button)) continue;
                    // DatePicker arrows are specialized skin controls, not ordinary Shale actions.
                    if (button.getStyleClass().contains("arrow-button")) continue;
                    assertTrue(button.getStyleClass().contains("shale-control-button"), "Ordinary workspace actions must keep semantic styling in " + theme);
                }
                assertNotNull(f.workspace.view().getBackground(), "Both themes must resolve the shared content surface");
                String preview = System.getProperty("shale.preview.dir");
                if (preview != null) {
                    var snapshot = f.workspace.view().snapshot(null, null);
                    var pixels = snapshot.getPixelReader();
                    var png = new java.awt.image.BufferedImage((int)snapshot.getWidth(), (int)snapshot.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    for (int y=0;y<png.getHeight();y++) for (int x=0;x<png.getWidth();x++) png.setRGB(x,y,pixels.getArgb(x,y));
                    java.nio.file.Files.createDirectories(java.nio.file.Path.of(preview));
                    javax.imageio.ImageIO.write(png, "png", java.nio.file.Path.of(preview, "case-updates-" + theme + ".png").toFile());
                }
            }
            manager.unregister(scene);
        });
    }

    private static CaseUpdateDto update(long id,String text) { return new CaseUpdateDto(id,501,text,AT,AT,31,"Author",VERSION,false); }
    private static final class Fixture {
        AppState state=new AppState(); List<CaseUpdateQuery> queries=new ArrayList<>();
        CaseUpdatePage page=new CaseUpdatePage(List.of(),null); boolean fail, failAuthors; int creates, authorLoads;
        java.util.function.Consumer<Runnable> dispatch=Runnable::run;
        final CaseServicePort service;
        final CaseUpdatesWorkspaceController workspace;
        Fixture(boolean admin) {
            state.setShaleClientId(41);state.setUserId(31);state.setAdmin(admin);
            service=(CaseServicePort) Proxy.newProxyInstance(CaseServicePort.class.getClassLoader(),new Class<?>[]{CaseServicePort.class},(p,m,a)-> switch(m.getName()) {
                case "searchCaseUpdates" -> { queries.add((CaseUpdateQuery)a[0]); if(fail)throw new IllegalStateException("test failure"); yield page; }
                case "listCaseUpdateAuthors" -> { authorLoads++; if(failAuthors) throw new IllegalStateException("Author load failed"); yield List.of(new CaseUpdateAuthorDto(17,"Same Name",null)); }
                case "addCaseNote" -> { creates++; yield null; }
                case "updateCaseNote" -> true;
                case "getPrimaryCaseLink" -> null;
                default -> throw new AssertionError("Unexpected service call: "+m.getName());
            });
            workspace=new CaseUpdatesWorkspaceController(501,service,state,Runnable::run,r->dispatch.accept(r),dto->new Label(dto.getNoteText()),()->{});
        }
        CaseUpdateQuery last(){return queries.getLast();}
    }
    @SuppressWarnings("unchecked") private static ComboBox<Object> combo(Object target,String name){return field(target,name,ComboBox.class);}
    private static <T> T field(Object target,String name,Class<T> type) {
        try{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return type.cast(f.get(target));}
        catch(Exception ex){throw new AssertionError(ex);}
    }
    private static void set(Object target,String name,Object value) throws Exception {Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
    private static Object invoke(Object target,String name) throws Exception{return invoke(target,name,new Class<?>[0]);}
    private static Object invoke(Object target,String name,Class<?>[] types,Object...args) throws Exception{Method m=target.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(target,args);}
    private static final class DirectExecutor extends AbstractExecutorService {
        public void shutdown(){} public List<Runnable> shutdownNow(){return List.of();} public boolean isShutdown(){return false;}
        public boolean isTerminated(){return false;} public boolean awaitTermination(long time,TimeUnit unit){return true;}
        public void execute(Runnable job){job.run();}
    }
}
