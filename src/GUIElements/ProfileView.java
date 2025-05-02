package GUIElements;

import application.Main;
import connections.ConnectionResources;
import connections.Server;
import dataStructures.Organization;
import dataStructures.User;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BackgroundImage;
import javafx.scene.layout.BackgroundPosition;
import javafx.scene.layout.BackgroundRepeat;
import javafx.scene.layout.BackgroundSize;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

public class ProfileView extends FlowPane {
	/*
	 * Server Properties
	 */
//	private int id;
	private String nameFirst;
	private String nameLast;
	private String email;
	private String password;
	private String color; /* hexadecimal 0x00000000 format */
	private boolean attorney;
	private boolean admin;
	private boolean deleted;
	private int defaultOrganizationId;
	private String initials;
	private User user;
	/*
	 * need to add Phone Address
	 * 
	 */

	public ProfileView(User user) {

//		id = user.get_id();
		nameFirst = user.getNameFirst();
		nameLast = user.getNameLast();
		email = user.getEmail();
		password = user.getPassword();
		color = user.getColor();
		attorney = user.isIs_attorney();
		admin = user.isIs_admin();
		deleted = user.isIs_deleted();
		defaultOrganizationId = user.getDefault_organization();
		initials = user.getInitials();
		this.user = user;

		this.setAlignment(Pos.CENTER);
		this.setHgap(10);
		this.setVgap(10);
		this.setPadding(new Insets(10));

		setupPersonalBox();
		setupShaleInfoBox();
		setupOrganizationBox();
	}

	private void setupPersonalBox() {
		VBox box = new VBox();
		box.setPadding(new Insets(10));
		box.setSpacing(10);
		box.setAlignment(Pos.TOP_LEFT);

		Label title = new Label("Personal Information");
		title.setStyle("-fx-font-weight: bold");
		title.setUnderline(true);
		box.getChildren().add(title);

		TextField nameFirst = new TextField();
		nameFirst.setPromptText("First Name");
		nameFirst.setText(this.nameFirst);
		nameFirst.focusedProperty().addListener(new ChangeListener<Boolean>() {

			@Override
			public void changed(ObservableValue<? extends Boolean> arg0, Boolean oldValue, Boolean newValue) {

				Task<Integer> t = new Task<Integer>() {

					@Override
					protected Integer call() throws Exception {
						if (newValue != oldValue) {
							Main.getCurrentUser().setNameFirst(nameFirst.getText());
							Server.updateUserStringField(user.get_id(), "name_first", nameFirst.getText(), ConnectionResources.getConnection());
						}
						return null;
					}
				};
				new Thread(t).start();

			}
		});
		box.getChildren().add(nameFirst);

		TextField nameLast = new TextField();
		nameLast.setPromptText("Last Name");
		nameLast.setText(this.nameLast);
		nameLast.focusedProperty().addListener(new ChangeListener<Boolean>() {

			@Override
			public void changed(ObservableValue<? extends Boolean> arg0, Boolean oldValue, Boolean newValue) {
				Task<Integer> t = new Task<Integer>() {

					@Override
					protected Integer call() throws Exception {
						if (newValue != oldValue) {
							Main.getCurrentUser().setNameLast(nameLast.getText());
							Server.updateUserStringField(user.get_id(), "name_last", nameLast.getText(), ConnectionResources.getConnection());
						}
						return null;
					}
				};
				new Thread(t).start();
			}
		});
		box.getChildren().add(nameLast);

		TextField initials = new TextField();
		initials.setPromptText("Initials");
		initials.setText(this.initials);
		initials.focusedProperty().addListener(new ChangeListener<Boolean>() {

			@Override
			public void changed(ObservableValue<? extends Boolean> arg0, Boolean oldValue, Boolean newValue) {
				Task<Integer> t = new Task<Integer>() {

					@Override
					protected Integer call() throws Exception {
						if (newValue != oldValue) {
							Main.getCurrentUser().setInitials(initials.getText());
							Server.updateUserStringField(user.get_id(), "initials", initials.getText(), ConnectionResources.getConnection());
						}
						return null;
					}
				};
				new Thread(t).start();
			}
		});
		box.getChildren().add(initials);

		setupBorder(box);

		setupBackground(box, Color.LIGHTBLUE);

		this.getChildren().add(box);

	}

	private void setupShaleInfoBox() {
		VBox box = new VBox();
		box.setPadding(new Insets(10));
		box.setSpacing(10);
		box.setAlignment(Pos.TOP_LEFT);

		Label title = new Label("Shale Info");
		title.setStyle("-fx-font-weight: bold");
		title.setUnderline(true);
		box.getChildren().add(title);

		TextField email = new TextField();
		email.setPromptText("Email Address");
		email.setText(this.email);
		email.setDisable(true);
		box.getChildren().add(email);

		HBox pwBox = new HBox();
		pwBox.setSpacing(10);

		Button pw = new Button("Change");

		TextField password = new TextField();
		password.setPromptText("Password");
		password.setText(this.password);
		password.setDisable(true);

		pwBox.getChildren().add(password);
		pwBox.getChildren().add(pw);
		box.getChildren().add(pwBox);

		HBox box2 = new HBox();
		box2.setSpacing(10);
		box2.setAlignment(Pos.CENTER_LEFT);
		box2.setPadding(new Insets(5));

		RadioButton isAttorney = new RadioButton("Attorney");
		isAttorney.setSelected(attorney);
		isAttorney.setDisable(true);
		box2.getChildren().add(isAttorney);

		RadioButton isAdmin = new RadioButton("Admin");
		isAdmin.setSelected(admin);
		isAdmin.setDisable(true);
		box2.getChildren().add(isAdmin);

		RadioButton isDeleted = new RadioButton("Deleted");
		isDeleted.setSelected(deleted);
		isDeleted.setDisable(true);
		box2.getChildren().add(isDeleted);

		if (!admin) {
			isDeleted.setVisible(false);

			isAdmin.setDisable(true);
			isAttorney.setDisable(true);
		} else {
			isDeleted.setVisible(true);
		}
		if (attorney) {
			isAttorney.setDisable(false);
		}

		box.getChildren().add(box2);

		Label colorTitle = new Label("Choose a Color");
		title.setStyle("-fx-font-weight: bold");
		box.getChildren().add(colorTitle);

		ColorPicker cp = new ColorPicker();
		if (color != "")
			cp.setValue(Color.valueOf(color));
		else
			cp.setValue(Color.WHITE);
		cp.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				Color newColor = cp.getValue();
				color = newColor.toString();
				Server.updateUserStringField(user.get_id(), "color", color, ConnectionResources.getConnection());
				Main.getCurrentUser().setColor(color);
				Main.getUsers().get(Main.getCurrentUser().get_id()).setColor(color);
			}
		});

		pw.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				Stage stage = new Stage();
				FlowPane pwPane = new FlowPane();
				pwPane.setHgap(5);
				pwPane.setVgap(5);
				pwPane.setAlignment(Pos.CENTER);
				pwPane.setPadding(new Insets(10));
				pwPane.setBackground(new Background(new BackgroundImage(Main.getBackgroundImage(), BackgroundRepeat.NO_REPEAT,
						BackgroundRepeat.NO_REPEAT, BackgroundPosition.CENTER, new BackgroundSize(1.0, 1.0, true, true, false, false))));

				VBox pwVBox = new VBox();
				pwVBox.setSpacing(5);
				pwVBox.setAlignment(Pos.TOP_CENTER);

				Label current = new Label("Enter your old password");
				current.setTextFill(Color.WHITE);
				current.setBackground(Main.getMenuBackground());

				TextField pwCurrent = new TextField();
				pwCurrent.setPromptText("Current Password...");

				Label newPassword = new Label("Enter your new password");
				newPassword.setTextFill(Color.WHITE);
				newPassword.setBackground(Main.getMenuBackground());

				TextField pwNew = new TextField();
				pwNew.setPromptText("New Password");

				Label confirmPassword = new Label("Confirm new password");
				confirmPassword.setTextFill(Color.WHITE);
				confirmPassword.setBackground(Main.getMenuBackground());

				TextField pwConfirm = new TextField();
				pwConfirm.setPromptText("Confirm New Password");

				HBox saveCancel = new HBox();
				saveCancel.setSpacing(10);
				saveCancel.setAlignment(Pos.CENTER);

				Button save = new Button("Save");
				save.setDisable(true);
				save.setOnAction(new EventHandler<ActionEvent>() {

					@Override
					public void handle(ActionEvent arg0) {
						System.out.println("Save password here");
						Main.getCurrentUser().setPassword(pwConfirm.getText());

						Server.updateUserStringField(user.get_id(), "password", pwConfirm.getText(), ConnectionResources.getConnection());

						password.setText(pwConfirm.getText());

						stage.close();

					}
				});

				Button cancel = new Button("Cancel");
				cancel.setOnAction(new EventHandler<ActionEvent>() {

					@Override
					public void handle(ActionEvent arg0) {
						stage.close();

					}
				});

				pwConfirm.setOnKeyTyped(new EventHandler<Event>() {

					@Override
					public void handle(Event arg0) {
						if (!pwConfirm.getText().equals("")) {
							if (pwConfirm.getText().equals(pwNew.getText())) {
								save.setDisable(false);
							} else
								save.setDisable(true);
						}

					}
				});

				pwNew.setDisable(true);
				pwConfirm.setDisable(true);
				pwCurrent.setOnKeyTyped(new EventHandler<Event>() {

					@Override
					public void handle(Event arg0) {
						if (pwCurrent.getText().equals(user.getPassword())) {
							pwNew.setDisable(false);
							pwConfirm.setDisable(false);
						} else {
							pwNew.setDisable(true);
							pwConfirm.setDisable(true);
						}
					}
				});

				saveCancel.getChildren().add(save);
				saveCancel.getChildren().add(cancel);

				pwVBox.getChildren().add(current);
				pwVBox.getChildren().add(pwCurrent);
				pwVBox.getChildren().add(newPassword);
				pwVBox.getChildren().add(pwNew);
				pwVBox.getChildren().add(confirmPassword);
				pwVBox.getChildren().add(pwConfirm);
				pwVBox.getChildren().add(saveCancel);

				pwPane.getChildren().add(pwVBox);

				Scene scene = new Scene(pwPane, 300, 250);

				stage.setScene(scene);
				stage.show();

			}
		});

		box.getChildren().add(cp);

		setupBorder(box);

		setupBackground(box, Color.LIGHTGREEN);

		this.getChildren().add(box);

	}

	private void setupOrganizationBox() {
		VBox box = new VBox();
		box.setPadding(new Insets(10));
		box.setSpacing(10);
		box.setAlignment(Pos.TOP_LEFT);

		Label title = new Label("Organization Info");
		title.setStyle("-fx-font-weight: bold");
		title.setUnderline(true);
		box.getChildren().add(title);

		Organization org = Main.getOrganizations().get(defaultOrganizationId);

		Label name = new Label("Current Organization:");
		name.setStyle("-fx-font-weight: bold");
		box.getChildren().add(name);

		Label orgName = new Label(org.getName());
		box.getChildren().add(orgName);

		Label id = new Label("Organization ID:");
		id.setStyle("-fx-font-weight: bold");
		box.getChildren().add(id);

		Label orgId = new Label(String.valueOf(org.get_id()));
		box.getChildren().add(orgId);

		Label desc = new Label("Description:");
		desc.setStyle("-fx-font-weight: bold");
		box.getChildren().add(desc);

		Label orgDes = new Label(org.getDescription());
		box.getChildren().add(orgDes);

		setupBorder(box);

		setupBackground(box, Color.LIGHTCORAL);

		this.getChildren().add(box);

	}

	private void setupBorder(VBox box) {
		box.setBorder(new Border(new BorderStroke(Color.BLACK, BorderStrokeStyle.SOLID, new CornerRadii(2), new BorderWidths(3))));

	}

	private void setupBackground(VBox box, Color color) {
		box.setBackground(new Background(new BackgroundFill(color, new CornerRadii(2), new Insets(1))));

	}

}
