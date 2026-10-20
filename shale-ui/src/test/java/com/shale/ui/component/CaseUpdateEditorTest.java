package com.shale.ui.component;

import com.shale.ui.testutil.JavaFxTestSupport;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class CaseUpdateEditorTest {
    @Test void creationAndModificationUseSameWrappedResizableEditorAndSingleSave() {
        for(String initial:List.of("","existing update")) {
            AtomicReference<Dialog<String>> opened=new AtomicReference<>();
            AtomicReference<CompletableFuture<String>> pending=new AtomicReference<>();
            AtomicInteger calls=new AtomicInteger();
            JavaFxTestSupport.runAndWait(()->{
                Dialog<String> dialog=EnhancedTextArea.createEditorDialog(null,"Case Update",initial,false,value->{
                    assertEquals("saved draft",value); calls.incrementAndGet();
                    var future=new CompletableFuture<String>();pending.set(future);return future;
                });
                opened.set(dialog);dialog.show();
                assertTrue(dialog.isResizable());
                RichTextExpandedEditor editor=editor(dialog);
                assertTrue(editor.area().isWrapText());
                editor.area().replaceText("saved draft");
                Button save=button(dialog,"Save"); assertFalse(save.isDefaultButton(),"Enter must remain multiline input");
                assertTrue(save.getStyleClass().contains("shale-control-primary"));
                save.fire();save.fire();assertEquals(1,calls.get());assertTrue(dialog.isShowing());
                dialog.close(); assertTrue(dialog.isShowing(),"Closing during Save must be vetoed");
                pending.get().complete(null);
            });
            JavaFxTestSupport.runAndWait(()->{assertFalse(opened.get().isShowing());assertEquals("saved draft",opened.get().getResult());});
        }
    }

    @Test void failureAndBlankValidationKeepDraftForRetryAndCancellationNeverSaves() {
        AtomicReference<Dialog<String>> opened=new AtomicReference<>(); AtomicInteger calls=new AtomicInteger();
        JavaFxTestSupport.runAndWait(()->{
            Dialog<String> dialog=EnhancedTextArea.createEditorDialog(null,"Case Update","",false,value->{calls.incrementAndGet();return CompletableFuture.completedFuture("Conflict. Reopen the latest update.");});
            opened.set(dialog);dialog.show(); button(dialog,"Save").fire();assertEquals(0,calls.get());
            editor(dialog).area().replaceText("my draft");button(dialog,"Save").fire();
        });
        JavaFxTestSupport.runAndWait(()->{
            Dialog<String> dialog=opened.get();assertTrue(dialog.isShowing());assertEquals("my draft",editor(dialog).markdown());
            assertEquals(1,calls.get());
            chooseDiscard("Keep Editing");button(dialog,"Cancel").fire();assertTrue(dialog.isShowing());
            assertEquals("my draft",editor(dialog).markdown());
            chooseDiscard("Discard");button(dialog,"Cancel").fire();assertFalse(dialog.isShowing());
            assertNull(dialog.getResult());assertEquals(1,calls.get(),"Cancellation must never invoke persistence");
        });
    }

    @Test void closeAndEscapeEquivalentProtectDraftAndUnchangedCancelNeedsNoPrompt() {
        JavaFxTestSupport.runAndWait(()->{
            Dialog<String> dialog=EnhancedTextArea.createEditorDialog(null,"Edit Case Update","original",false,value->{fail("Cancel must not save");return null;});
            dialog.show();editor(dialog).area().replaceText("changed");
            chooseDiscard("Keep Editing");dialog.close();assertTrue(dialog.isShowing());
            chooseDiscard("Discard");dialog.close();assertFalse(dialog.isShowing());assertNull(dialog.getResult());
            Dialog<String> unchanged=EnhancedTextArea.createEditorDialog(null,"Edit Case Update","**original**",false,value->{fail("Cancel must not save");return null;});
            unchanged.show();button(unchanged,"Cancel").fire();assertFalse(unchanged.isShowing());
        });
    }

    private static RichTextExpandedEditor editor(Dialog<?> dialog){return (RichTextExpandedEditor)((VBox)dialog.getDialogPane().getContent()).getChildren().getFirst();}
    private static Button button(Dialog<?> dialog,String label){return (Button)dialog.getDialogPane().lookupButton(dialog.getDialogPane().getButtonTypes().stream().filter(b->b.getText().equals(label)).findFirst().orElseThrow());}
    private static void chooseDiscard(String label){
        Platform.runLater(()->Window.getWindows().stream().filter(Window::isShowing).map(Window::getScene)
                .filter(s->s!=null).flatMap(s->s.getRoot().lookupAll(".button").stream()).filter(Button.class::isInstance)
                .map(Button.class::cast).filter(b->b.getText().equals(label)).findFirst().orElseThrow().fire());
    }
}
