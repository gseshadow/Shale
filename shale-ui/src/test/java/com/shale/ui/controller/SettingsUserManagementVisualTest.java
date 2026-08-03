package com.shale.ui.controller;

import com.shale.data.dao.UserDao;
import com.shale.ui.testutil.JavaFxTestSupport;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.TableView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

final class SettingsUserManagementVisualTest {
    @Test void rendersActualUserManagementCardCellsAtNormalMinimumAndAfterReuse() {
        Assumptions.assumeTrue(System.getenv("DISPLAY") != null, "graphical runtime unavailable");
        JavaFxTestSupport.runAndWait(() -> { try {
            FXMLLoader loader=new FXMLLoader(getClass().getResource("/fxml/settings.fxml"));
            Parent root=loader.load();
            @SuppressWarnings("unchecked") TableView<SettingsController.UserManagementViewRow> table=(TableView<SettingsController.UserManagementViewRow>)loader.getNamespace().get("userManagementTable");
            VBox section=(VBox)loader.getNamespace().get("userAdministrationSection");section.setVisible(true);section.setManaged(true);section.setPrefWidth(960);section.setMaxWidth(Double.MAX_VALUE);
            List<SettingsController.UserManagementViewRow> rows=new ArrayList<>();
            rows.add(row(101,"Alex","Morgan","Alex Morgan","AM","#2B8A9A",false));
            rows.add(row(102,"Alex","Morgan","Alex Morgan","AX","#9B59B6",false));
            rows.add(row(103,"Jordan","Lee","Jordan Lee","JL","#D97706",true));
            rows.add(row(104,"An exceptionally long first name","With a deliberately long family name","An exceptionally long first name With a deliberately long family name","LW","#167D54",false));
            for(int i=0;i<14;i++) rows.add(row(200+i,"User","Number "+i,"User Number "+i,"U"+(i%10),i%2==0?"#3568A8":"#A84F72",false));
            table.getItems().setAll(rows); table.getSelectionModel().select(1);
            ((Pane)section.getParent()).getChildren().remove(section);
            StackPane visualRoot=new StackPane(section);visualRoot.getStyleClass().add("app-shell");
            Scene scene=new Scene(visualRoot,1000,520);scene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
            Stage stage=new Stage();stage.setScene(scene);stage.show();visualRoot.applyCss();visualRoot.layout();section.applyCss();section.layout();
            Path output=Path.of("target/visual-verification");Files.createDirectories(output);
            snapshot(section,output.resolve("user-management-mini-cards-normal.png"));
            stage.setWidth(900);stage.setHeight(500);section.setPrefWidth(860);visualRoot.resize(900,500);section.resize(860,476);visualRoot.applyCss();visualRoot.layout();section.applyCss();section.layout();
            snapshot(section,output.resolve("user-management-mini-cards-minimum.png"));
            table.scrollTo(rows.size()-1);table.getSelectionModel().select(rows.size()-1);visualRoot.applyCss();visualRoot.layout();section.applyCss();section.layout();
            snapshot(section,output.resolve("user-management-mini-cards-after-scroll-reuse.png"));
            assertFalse(section.lookupAll(".user-card-mini").isEmpty(),"render must contain shared mini card shells");
            assertTrue(section.lookupAll(".user-card-table-name").size()>1);
            stage.close();
        } catch(Exception e){throw new AssertionError(e);} });
    }
    private static SettingsController.UserManagementViewRow row(int id,String first,String last,String display,String initials,String color,boolean inactive){return new SettingsController.UserManagementViewRow(new UserDao.UserManagementRow(id,first,last,display,display.toLowerCase().replace(' ','.')+"@example.test","",color,initials,false,false,inactive,false,new byte[]{1}));}
    private static void snapshot(VBox node,Path path)throws Exception{WritableImage image=node.snapshot(new SnapshotParameters(),null);BufferedImage buffered=new BufferedImage((int)image.getWidth(),(int)image.getHeight(),BufferedImage.TYPE_INT_ARGB);
        int[] pixels=new int[(int)image.getWidth()*(int)image.getHeight()];image.getPixelReader().getPixels(0,0,(int)image.getWidth(),(int)image.getHeight(),javafx.scene.image.PixelFormat.getIntArgbInstance(),pixels,0,(int)image.getWidth());buffered.setRGB(0,0,buffered.getWidth(),buffered.getHeight(),pixels,0,buffered.getWidth());ImageIO.write(buffered,"png",path.toFile());assertTrue(Files.size(path)>1000,path.toString());}
}
