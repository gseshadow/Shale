package GUIElements;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import application.Main;
import connections.ConnectionResources;
import connections.Server;
import dataStructures.User;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Duration;

public class Login extends FlowPane {
	private TextField email = new TextField();
	private PasswordField password = new PasswordField();
	private Label passwordShow = new Label();
	private RadioButton stay = new RadioButton("Stay Logged In");
	private Connection connection = null;
	private boolean newUser = false;
	private boolean authenticated = false;

	private ImageView shaleImageView;
	private Image shaleImage;
	private File shaleFile;

	public Login() {

		if (Main.isGlobalDebug())
			logThis("Login...");

		this.setMinHeight(725);
		this.setHgap(10);
		this.setVgap(10);
		this.setPadding(new Insets(5));
		this.setAlignment(Pos.TOP_CENTER);
		shaleFile = new File("Local" + System.getProperty("file.separator") + "Shale.png");
		shaleImage = new Image(shaleFile.toURI().toString());
		shaleImageView = new ImageView(shaleImage);
		shaleImageView.setFitHeight(529);
		shaleImageView.setFitWidth(900);
		VBox loginBox = new VBox();
		loginBox.setSpacing(10);
		loginBox.setPadding(new Insets(5));
		loginBox.setAlignment(Pos.CENTER);
		loginBox.setBackground(new Background(new BackgroundFill(Color.LIGHTBLUE, new CornerRadii(2), new Insets(1))));
		loginBox.setBorder(new Border(new BorderStroke(Color.BLACK, BorderStrokeStyle.SOLID, new CornerRadii(2), new BorderWidths(3))));
		loginBox.setMaxSize(300, 200);

		TranslateTransition st = new TranslateTransition();
		st.setFromY(1500);
		st.setToY(350);
		st.setDuration(Duration.seconds(1.5));
		st.setNode(loginBox);
		st.play();
		VBox leftAligned = new VBox();
		leftAligned.setAlignment(Pos.CENTER_LEFT);
		leftAligned.setSpacing(5);

		HBox nameBox = new HBox();
		nameBox.setSpacing(5);
		nameBox.setAlignment(Pos.CENTER);

		leftAligned.getChildren().add(nameBox);
		Label emailLabel = new Label("Email Address");

		if (Main.isGlobalDebug())
			emailLabel.setStyle("-fx-text-fill: red ");

		emailLabel.setOnMouseClicked(new EventHandler<Event>() {

			@Override
			public void handle(Event arg0) {
				if (Main.isGlobalDebug()) {
					emailLabel.setStyle("-fx-text-fill: black ");
					Main.setGlobalDebug(false);
				} else {
					emailLabel.setStyle("-fx-text-fill: red ");
					Main.setGlobalDebug(true);
				}
			}
		});

		email.setPromptText("Email");
		email.setAlignment(Pos.CENTER);

		leftAligned.getChildren().add(emailLabel);
		leftAligned.getChildren().add(email);

		CheckBox revealHide = new CheckBox("Show");
		revealHide.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				passwordShow.setText(password.getText());
				if (revealHide.isSelected()) {
					passwordShow.setVisible(true);
				} else {
					passwordShow.setVisible(false);
				}

			}
		});

		Label pass = new Label("Password:     ");
		password.setPromptText("Password*");
		password.setAlignment(Pos.CENTER);
		password.setPrefWidth(230);
		password.setOnKeyReleased(new EventHandler<KeyEvent>() {

			@Override
			public void handle(KeyEvent key) {
				if (key.getCode() == KeyCode.ENTER) {
					login();
				} else {
					passwordShow.setText(password.getText());
				}

			}
		});

		HBox passLabel = new HBox();
		passLabel.setAlignment(Pos.CENTER_LEFT);
		passLabel.setSpacing(5);
		passLabel.getChildren().add(pass);
		passwordShow.setVisible(false);
		passLabel.getChildren().add(passwordShow);

		leftAligned.getChildren().add(passLabel);

		HBox passBox = new HBox();
		passBox.setAlignment(Pos.CENTER);
		passBox.setSpacing(5);
		passBox.getChildren().add(password);
		passBox.getChildren().add(revealHide);

		leftAligned.getChildren().add(passBox);

		loginBox.getChildren().add(leftAligned);

		HBox buttonBox = new HBox();
		buttonBox.setSpacing(5);
		buttonBox.setAlignment(Pos.CENTER);

		Button login = new Button("Login");
		login.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				login();
			}
		});
		buttonBox.getChildren().add(login);

		Label or = new Label("or");
		// buttonBox.getChildren().add(or);// TODO

		Button signUp = new Button("Sign Up");
		signUp.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				if (signUp.getText().equals("Sign Up")) {
					signUp.setText("Already Have a Login");
					login.setText("Create New Account");
					or.setVisible(false);
					newUser = true;
				} else {
					signUp.setText("Sign Up");
					login.setText("Login");
					or.setVisible(true);
					newUser = false;
				}

			}
		});
//		buttonBox.getChildren().add(signUp);//TODO
		loginBox.getChildren().add(buttonBox);

		loadUser();

		stay.setSelected(Main.getCurrentUser().isStayLoggedIn());
		stay.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				Main.getCurrentUser().setStayLoggedIn(stay.isSelected());

			}
		});

		loginBox.getChildren().add(stay);

		StackPane stack = new StackPane();
		stack.getChildren().add(shaleImageView);
		stack.getChildren().add(loginBox);

		st.play();

		this.getChildren().add(stack);

		if (Main.getCurrentUser().isStayLoggedIn() && Main.getCurrentUser().isLoggedIn()) {
			login();
		}
	}

	private void loadUser() {
		if (Main.isGlobalDebug())
			logThis("Login.loadUser()");

		File file = new File(
				Main.getDefaultLocation() + System.getProperty("file.separator") + "Resources" + System.getProperty("file.separator") + "UserData");
		try {
			FileInputStream fis = new FileInputStream(file);
			ObjectInputStream ois = new ObjectInputStream(fis);
			User user = (User) ois.readObject();
			Main.setCurrentUser(user);
//			nameFirst.setText(user.getNameFirst());
//			nameLast.setText(user.getNameLast());
			email.setText(user.getEmail());
			if (user.isStayLoggedIn())
				password.setText(user.getPassword());
			ois.close();

		} catch (Exception e) {

			System.out.println("Login.loadUser() - Failed to load user");
			try {
				Files.write(Paths.get(Main.getLog().getAbsolutePath()), "\nLogin.loadUser() - Failed to load user".getBytes(), StandardOpenOption.APPEND);
			} catch (IOException en) {
				en.printStackTrace();
			}
		}

	}

//You just completed connection to the database on the login screen
	private void login() {
		if (Main.isGlobalDebug())
			logThis("Login.login()");

		if (email.getText().equals("")) {
			email.setPromptText("Must Enter Email");
		} else if (!newUser) {
			Main.getController().changeCenter(Main.getLoading());
			Main.getController().hideScrollBars(true);
			Task<Integer> tas = new Task<Integer>() {

				@Override
				protected Integer call() throws Exception {

					try {
						if (Main.isGlobalDebug())
							logThis("Login.login() : Attempting Login");

						connection = ConnectionResources.getConnection();
						if (Main.isGlobalDebug())
							logThis("Login.login() : Connected");
						String queryUsersForEmail = "SELECT * FROM [dbo].[User] WHERE email = '" + email.getText().toLowerCase() + "'";

						PreparedStatement ps = connection.prepareStatement(queryUsersForEmail);
						ResultSet rs = ps.executeQuery();

						if (!rs.next()) {// No email match
							email.clear();
							email.setPromptText("Invalid email address.");

						} else {// Email match.//
							if (Main.isGlobalDebug())
								logThis("Login.login() : Email Match");
							/*
							 * Print out user information
							 */
							if (Main.isGlobalDebug()) {
								System.out.println("ID: " + rs.getInt(1));
								System.out.println("First Name: " + rs.getString(2));
								System.out.println("Last Name: " + rs.getString(3));
								System.out.println("Email: " + rs.getString(4));
								System.out.println("Password: " + rs.getString(5));
								System.out.println("Color: " + rs.getString(6));
								System.out.println("Is Attorney: " + rs.getBoolean(7));
								System.out.println("Is Admin: " + rs.getBoolean(8));
								System.out.println("Is Deleted: " + rs.getBoolean(9));
								System.out.println("Def Org Id: " + rs.getInt(10));
								System.out.println("Org Id: " + rs.getInt(11));
							}

							if (password.getText().equals(rs.getString(5))) {// Password and email match.//
								if (Main.isGlobalDebug())
									logThis("Login.login() : Password Match");

								Task<Integer> t = new Task<Integer>() {

									@Override
									protected Integer call() throws Exception {
										/*
										 * 
										 */
										try {

											double serverVersion = Server.getUpdate(Main.getVersion(), ConnectionResources.getConnection());
											if (Main.isGlobalDebug()) {
												logThis("Login.login() : Checking for updates");
												logThis("Server Version: " + serverVersion);
												logThis("Local Version: " + Main.getVersion());
											}
											if (serverVersion > Main.getVersion()) {
												Alert alert = new Alert(AlertType.CONFIRMATION);
												((Stage) alert.getDialogPane().getScene().getWindow()).getIcons().add(Main.getLogo());
												alert.setTitle("Update Available");
												alert.setHeaderText("An update is available. Would you like to install it now?");
												alert.setContentText("Selecting OK will close the application and install updates.");
												Optional<ButtonType> result = alert.showAndWait();
												if (result.isPresent() && result.get() == ButtonType.OK) {
													if (Main.isGlobalDebug()) {
														logThis("Login.login() : Starting Update");

														Process process;
														try {
															process = new ProcessBuilder(
																	System.getProperty("user.home") + System.getProperty("file.separator") + "AppData"
																			+ System.getProperty("file.separator") + "Local"
																			+ System.getProperty("file.separator") + "Shale"
																			+ System.getProperty("file.separator") + "ShaleUp.exe")
																	.start();// TODO
															// make
															// system
															// specific
															if (Main.isGlobalDebug())
																System.out.println(process.info());

															try {
																File temp = new File(Main.getDefaultBackgroundLocation() + System.getProperty("file.separator") + "Resources"
																		+ System.getProperty("file.separator") + "lock.lk");
																if (temp.exists()) {
																	temp.delete();
																}

																Platform.exit();
																System.exit(0);
															} catch (Exception e) {
																e.printStackTrace();
															}
														} catch (IOException e) {
															e.printStackTrace();
															System.exit(0);
														}
													}

												}
											} else {
												if (Main.isGlobalDebug())
													logThis("Login.login() : No Updates");
											}
										} catch (Exception e) {
											System.out.println(e.getMessage());
										}
										/*
										 * 
										 */
										return null;
									}
								};

								Platform.runLater(t);

								Main.startUpdater();

								/*
								 * Save user data to auto populate
								 */
								Main.getCurrentUser().setLoggedIn(true);
								Main.getCurrentUser().setStayLoggedIn(stay.isSelected());
								Main.getCurrentUser().setNameFirst(rs.getString(2));
								Main.getCurrentUser().setNameLast(rs.getString(3));
								Main.getCurrentUser().setEmail(email.getText());
								Main.getCurrentUser().setPassword(password.getText());
								Main.getCurrentUser().set_id(rs.getInt(1));
								Main.getCurrentUser().setColor(rs.getString(6));
								Main.getCurrentUser().setIs_attorney(rs.getBoolean(7));
								Main.getCurrentUser().setIs_admin(rs.getBoolean(8));
								Main.getCurrentUser().setIs_deleted(rs.getBoolean(9));
								Main.getCurrentUser().setDefault_organization(rs.getInt(10));

								authenticated = true;

								Main.loadLists();

							} else {
								authenticated = false;
							}

						}
						connection.close();
					} catch (SQLException e) {
					}
					return null;
				}
			};
			new Thread(tas).start();

			tas.setOnSucceeded(new EventHandler<WorkerStateEvent>() {

				@Override
				public void handle(WorkerStateEvent arg0) {
					if (authenticated) {

						if (Main.getCurrentUser().isShowActiveView()) {
							Main.getController().changeCenter(new ActiveCaseView());
						} else if (Main.getCurrentUser().isShowAsList()) {
							Main.getController().changeCenter(new PotentialsList());
						} else {
							Main.getController().changeCenter(new PotentialsBubbles());
						}

						Main.getController().changeLeft(false);

					} else {
						System.out.println("Not Authenticated");
						Main.getController().changeCenter(new Login());
					}
				}
			});
		} else

		{
			System.out.println("New User");
			// TODO add new user creation here

			newUser = false;
			login();
		}
	}

	private void logThis(String message) {
		System.out.println(message);
		try {
			Files.write(Paths.get(Main.getLog().getAbsolutePath()), ("\n" + message).getBytes(),
					StandardOpenOption.APPEND);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
