package application;

import java.awt.Dimension;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Scanner;

import com.hivemq.client.mqtt.MqttClientState;

import GUIElements.AlreadyRunning;
import GUIElements.IntakePane;
import GUIElements.LoadingScreen;
import GUIElements.MenuList;
import GUIElements.Terminal;
import connections.ConnectionResources;
import connections.Server;
import connections.Updater;
import application.util.ResourceManager;
import dataStructures.Case;
import dataStructures.Contact;
import dataStructures.Facility;
import dataStructures.Incident;
import dataStructures.Organization;
import dataStructures.PracticeArea;
import dataStructures.Provider;
import dataStructures.Status;
import dataStructures.User;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.Duration;
import tools.IO;

public class Main extends Application {

	private static Case currentCase;
	private static IntakePane currentIntake;
	private static String fileLocation;
	private static String defaultLocation;
	private static InputPaneController controller;
	private static User currentUser;
	private static LoadingScreen loading = new LoadingScreen();
	/*
	 * 
	 */
	private static HashMap<Integer, Case> cases = new HashMap<>();
	private static HashMap<Integer, Contact> contacts = new HashMap<>();
	private static HashMap<Integer, Facility> facilities = new HashMap<>();
	private static HashMap<Integer, Provider> providers = new HashMap<>();
	private static HashMap<Integer, Organization> organizations = new HashMap<>();
	private static HashMap<Integer, PracticeArea> practiceAreas = new HashMap<>();
	private static HashMap<Integer, Status> statusChoices = new HashMap<>();
	private static HashMap<Integer, User> users = new HashMap<>();
	private static HashMap<Integer, Incident> incidents = new HashMap<>();
//	private static HashMap<Integer, Task> tasks;
	/*
	 * 
	 */
	private static Background menuBackground;
	private static Updater updater;
	private static Image backgroundImage;
	private static File backgroundFile;
	private static String backgroundLocation;
	private static String defaultBackgroundLocation;
	private static int currentCaseId;

	private static Color menuBackgroundColor = new Color(.5, .5, .5, .3);

	private static boolean globalDebug = false;

	private static Terminal terminal;

	private static int then = LocalTime.now().getMinute();
	private static double version = 0.014; // TODO update version number
	private static String logName;
	private static File log;
	private static Image logo;

	public static Image getLogo() {
		return logo;
	}

	@Override
	public void start(Stage primaryStage) {

		try {

			/*
			 * Local file system setup
			 */

			menuBackground = new Background(new BackgroundFill(menuBackgroundColor, null, null));

                        logo = ResourceManager.loadImage("ShaleNoText.png");

			String appResources = System.getProperty("os.name");
			if (appResources.contains("Mac")) {
				appResources = "Applications" + System.getProperty("file.separator") + "Shale";

			} else {
				appResources = "AppData" + System.getProperty("file.separator")
						+ "Local" + System.getProperty("file.separator") + "Shale";
			}

			defaultLocation = System.getProperty("user.home") + System.getProperty("file.separator") + appResources;

			defaultBackgroundLocation = defaultLocation + System.getProperty("file.separator") + "Resources" + System.getProperty("file.separator")
					+ "Backgrounds" + System.getProperty("file.separator") + "Default.png";

			backgroundFile = new File(defaultBackgroundLocation);

			setBackgroundImage(new Image(backgroundFile.toURI().toString()));

			fileLocation = defaultLocation;

			startUpdater();

			File file = new File(fileLocation);

			try {
				if (file.mkdir()) {
					System.out.println("Directory Created");
				}

			} catch (Exception e) {
				e.printStackTrace();
			}

			file = new File(defaultLocation + System.getProperty("file.separator") + "Resources");
			try {
				if (file.mkdir()) {
					System.out.println("Directory Created");
				}
			} catch (Exception e) {
				e.printStackTrace();
			}

			/*
			 * Setup local backup
			 */
			file = new File(defaultLocation + System.getProperty("file.separator") + "LocalBackup");
			try {
				if (file.mkdir()) {
					System.out.println("Directory Created");
				}
			} catch (Exception e) {
				e.printStackTrace();
			}

			/*
			 * Setup logging
			 */
			setUpLogging();

			/*
			 * Setup single instance lock file
			 */
			File temp = new File(
					defaultLocation + System.getProperty("file.separator") + "Resources" + System.getProperty("file.separator") + "lock.lk");
			if (!temp.exists()) {
				temp.createNewFile();
			} else {

				if (globalDebug) {
					try {
						Files.write(Paths.get(log.getAbsolutePath()), "\n Main(single instance check) Already Running".getBytes(),
								StandardOpenOption.APPEND);
					} catch (IOException e) {
						e.printStackTrace();
					}
					System.out.println("Already Running");
				}

				Stage sttwo = new Stage();
				Scene scTwo = new Scene(new AlreadyRunning(sttwo));
				sttwo.getIcons().add(logo);
				sttwo.setScene(scTwo);
				sttwo.setOnCloseRequest(new EventHandler<WindowEvent>() {

					@Override
					public void handle(WindowEvent arg0) {
						Platform.exit();
						System.exit(0);
					}
				});

				sttwo.showAndWait();
			}

			/*
			 * Read and set file location to the URL specified in customLocation.txt. If not exists,
			 * create file with default location
			 */
			try {
				file = new File(defaultLocation + System.getProperty("file.separator") + "Resources" + System.getProperty("file.separator")
						+ "CustomLocation.txt");
				if (file.exists()) {
					/*
					 * there is a file in resources telling new location for intakes set file location to that
					 * URL
					 */
					Scanner scanner;

					scanner = new Scanner(file);

					setIntakeLocation(scanner.nextLine());

					scanner.close();
				} else {
					FileWriter fw = new FileWriter(file);
					fw.write(defaultLocation);
					fw.close();
					setIntakeLocation(fileLocation);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			/* copy IntakeFormBlank to AppData folder from the install folder */

                        Path intakeTemplate = Paths.get(defaultLocation + System.getProperty("file.separator") + "Resources"
                                        + System.getProperty("file.separator") + "IntakeFormBlank.docx");
                        try {
                                if (!Files.exists(intakeTemplate)) {
                                        ResourceManager.copyResource("IntakeFormBlank.docx", intakeTemplate, StandardCopyOption.REPLACE_EXISTING);
                                }
                        } catch (IOException e1) {
                                e1.printStackTrace();
                        }

                        /* copy default background to AppData folder from the install folder */

                        try {
                                ResourceManager.copyBackgroundAssets(Paths.get(defaultLocation + System.getProperty("file.separator") + "Resources"
                                                + System.getProperty("file.separator") + "Backgrounds"));
                        } catch (IOException e1) {
                                e1.printStackTrace();
                        }

			try {
				file = new File(defaultLocation + System.getProperty("file.separator") + "Resources" + System.getProperty("file.separator")
						+ "CustomBackgroundLocation.txt");
				if (file.exists()) {
					/*
					 * there is a file in resources telling new location for background set background
					 * location to that URL
					 */
					Scanner scanner;

					scanner = new Scanner(file);

					setBackgroundLocation(scanner.nextLine());

					scanner.close();

					setBackgroundLocation(backgroundLocation);
					setBackgroundImage(new Image(new File(backgroundLocation).toURI().toString()));
				} else {
					FileWriter fw = new FileWriter(file);
					fw.write(defaultBackgroundLocation);
					fw.close();
					setBackgroundImage(new Image(new File(defaultBackgroundLocation).toURI().toString()));
				}
			} catch (IOException e) {
				e.printStackTrace();
			}

			try {
				BorderPane root = (BorderPane) FXMLLoader.load(getClass().getResource("InputPane.fxml"));
				Dimension screenSize = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
				double width = screenSize.getWidth();
				double height = screenSize.getHeight();

				Scene scene = new Scene(root, width * (2.0 / 3.0), height * (4.0 / 5.0));
				scene.getStylesheets().add(getClass().getResource("application.css").toExternalForm());
				primaryStage.setScene(scene);
				primaryStage.show();
				primaryStage.setTitle("Shale v." + version);
				primaryStage.setMinWidth(700);
				primaryStage.setMinHeight(500);

				primaryStage.setOnCloseRequest(new EventHandler<WindowEvent>() {

					@Override
					public void handle(WindowEvent arg0) {
						IO.saveCurrent(currentUser);
						if (updater.getClient().getState() == MqttClientState.CONNECTED)
							updater.disconnect();
						try {
							File temp = new File(defaultLocation + System.getProperty("file.separator") + "Resources"
									+ System.getProperty("file.separator") + "lock.lk");
							if (temp.exists()) {
								temp.delete();
							}

							Platform.exit();
							System.exit(0);
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				});

				/*
				 * Idle program listener and function
				 */
				Task<Integer> t = new Task<Integer>() {

					@Override
					protected Integer call() throws Exception {

						scene.addEventFilter(Event.ANY, new EventHandler<Event>() {

							@Override
							public void handle(Event arg0) {// if mouse clicked or key pressed reset timer
								if (arg0.getEventType().toString().equals("MOUSE_CLICKED") || arg0.getEventType().toString().equals("KEY_PRESSED")) {
									then = LocalTime.now().getMinute();

								}

							}
						});

						Timeline idleCheck = new Timeline(new KeyFrame(Duration.seconds(60), new EventHandler<ActionEvent>() {
							@Override
							public void handle(ActionEvent arg0) {

								int now = LocalTime.now().getMinute();
								if ((now - then) < 0) {
									now += 60;
								}
								if (globalDebug)
									System.out.println("Idle check: " + (now - then));
								// if idle for 15 minutes logout
								if (getCurrentUser().isLoggedIn()) {
									if ((now - then) > 14) {

										MenuList.logout(true);

									}
								}
							}

						}));
						idleCheck.setCycleCount(Timeline.INDEFINITE);
						idleCheck.play();
						return null;
					}
				};
				new Thread(t).start();

				primaryStage.getIcons().add(logo);

			} catch (Exception e) {
				e.printStackTrace();
			}

		} catch (Exception e) {
			System.out.println(e.toString());
			Platform.exit();
		}
	}

	public File getBackgroundFile() {
		return backgroundFile;
	}

	public static void loadLists() {
		loading.setNotification("Loading: Organizations...");
		organizations = Server.getOrganizations(currentUser.getDefault_organization(), ConnectionResources.getConnection());

		if (globalDebug) {
			System.out.println();
			System.out.println("Organizations:");

			try {
				Files.write(Paths.get(log.getAbsolutePath()), "\nLoading Organizations".getBytes(), StandardOpenOption.APPEND);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		for (Organization o : organizations.values()) {
			if (globalDebug)
				System.out.println("> " + o.getName());
		}

		loading.setNotification("Loading: Facilities...");
		facilities = Server.getFacilities(currentUser.getDefault_organization(), ConnectionResources.getConnection());
		if (globalDebug) {
			System.out.println();
			System.out.println("Facilities:");
			try {
				Files.write(Paths.get(log.getAbsolutePath()), "\nLoading Facilities".getBytes(), StandardOpenOption.APPEND);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		for (Facility f : facilities.values()) {
			if (globalDebug)
				System.out.println("> " + f.getName());
		}

		loading.setNotification("Loading: Providers...");
		providers = Server.getProviders(currentUser.getDefault_organization(), ConnectionResources.getConnection());
		if (globalDebug) {
			System.out.println();
			System.out.println("Providers:");
			try {
				Files.write(Paths.get(log.getAbsolutePath()), "\nLoading Providers".getBytes(), StandardOpenOption.APPEND);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		for (Provider p : providers.values()) {
			if (globalDebug)
				System.out.println("> " + p.getName());
		}

		loading.setNotification("Loading: Users...");
		users = Server.getUsersByOrganization(currentUser.getDefault_organization(), ConnectionResources.getConnection());
		if (globalDebug) {
			System.out.println();
			System.out.println("Users:");
			try {
				Files.write(Paths.get(log.getAbsolutePath()), "\nLoading Users".getBytes(), StandardOpenOption.APPEND);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		for (User u : users.values()) {
			if (globalDebug)
				System.out.println("> " + u.getNameFull());
		}

		loading.setNotification("Loading: Practice Areas...");
		practiceAreas = Server.getPracticeAreas(currentUser.getDefault_organization(), ConnectionResources.getConnection());
		if (globalDebug) {
			System.out.println();
			System.out.println("Practice Areas:");
			try {
				Files.write(Paths.get(log.getAbsolutePath()), "\nLoading Practice Areas".getBytes(), StandardOpenOption.APPEND);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		for (PracticeArea pa : practiceAreas.values()) {
			if (globalDebug)
				System.out.println("> " + pa.getPracticeArea());
		}

		loading.setNotification("Loading: Status Choices...");
		statusChoices = Server.getStatusChoices(ConnectionResources.getConnection());
		if (globalDebug) {
			System.out.println();
			System.out.println("Status Choices:");
			try {
				Files.write(Paths.get(log.getAbsolutePath()), "\nLoading Status Choices".getBytes(), StandardOpenOption.APPEND);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		for (Status s : statusChoices.values()) {
			if (globalDebug)
				System.out.println("> " + s.getStatus());
		}

		loading.setNotification("Loading: Contacts...");
		contacts = Server.getContacts(currentUser.getDefault_organization(), ConnectionResources.getConnection());
		if (globalDebug) {
			System.out.println("");
			System.out.println("Contacts:");
			try {
				Files.write(Paths.get(log.getAbsolutePath()), "\nLoading Contacts".getBytes(), StandardOpenOption.APPEND);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		for (Contact c : contacts.values()) {
			if (globalDebug)
				System.out.println("> " + c.getNameFull());
		}

		loading.setNotification("Loading: Incidents...");
		incidents = Server.getIncidents(currentUser.getDefault_organization(), ConnectionResources.getConnection());
		if (globalDebug) {
			System.out.println();
			System.out.println("Incidents:");
			try {
				Files.write(Paths.get(log.getAbsolutePath()), "\nLoading Incidents".getBytes(), StandardOpenOption.APPEND);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		for (Incident i : incidents.values()) {
			if (globalDebug)
				System.out.println("> " + i.getIncidentCaseId());
		}
		loading.setNotification("Loading: Cases...");

		/*
		 * Uncomment for case format
		 */

		cases = Server.getCasesByOrganization(currentUser.getDefault_organization(), ConnectionResources.getConnection());

//		// TODO
//		/*
//		 * Restore local files with this code
//		 */
//		try {
//
//			File restore = new File("J:\\OneDrive\\Desktop\\TEMP\\Cruz, Michael");
//			FileInputStream fis = new FileInputStream(restore);
//			ObjectInputStream ois = new ObjectInputStream(fis);
//			Case cse = (Case) ois.readObject();
//			cases.put(cse.get_id(), cse);
//			ois.close();
//		} catch (Exception e) {
//			e.printStackTrace();
//		}

		if (globalDebug) {
			System.out.println();
			System.out.println("Cases:");
			try {
				Files.write(Paths.get(log.getAbsolutePath()), "\nLoading Cases".getBytes(), StandardOpenOption.APPEND);
			} catch (IOException e) {
				e.printStackTrace();
			}
			for (Case c : cases.values()) {
				System.out.println(c.get_id() + ": " + c.getCaseName());

			}
		}

	}

	public static void main(String[] args) {
		launch(args);
	}

	public static void setIntake(IntakePane in) {
		currentIntake = in;
	}

	public static IntakePane getCurrentIntake() {
		return currentIntake;
	}

	public static void setCase(Case cse) {
		currentCase = cse;
	}

	public static Case getCurrentCase() {
		return currentCase;
	}

	public static String getFileLocation() {
		return fileLocation;
	}

	public static void setController(InputPaneController inputPaneController) {
		controller = inputPaneController;

	}

	public static InputPaneController getController() {
		return controller;
	}

	public static void setIntakeLocation(String intakeLocation) {

		fileLocation = intakeLocation;
	}

	public static void setBackgroundLocation(String backgroundLoc) {
		backgroundLocation = backgroundLoc;
	}

	public static String getDefaultLocation() {
		return defaultLocation;
	}

	public static void setCurrentUser(User user) {
		currentUser = user;
	}

	public static User getCurrentUser() {
		if (currentUser == null)
			currentUser = new User();
		return currentUser;
	}

	public static HashMap<Integer, PracticeArea> getPracticeAreas() {
		return practiceAreas;
	}

	public static void setPracticeAreas(HashMap<Integer, PracticeArea> practiceAreas) {
		Main.practiceAreas = practiceAreas;
	}

	public static HashMap<Integer, User> getUsers() {
		return users;
	}

	public static void setUsers(HashMap<Integer, User> users) {
		Main.users = users;
	}

	public static HashMap<Integer, Status> getStatusChoices() {
		return statusChoices;
	}

	public static void setStatusChoices(HashMap<Integer, Status> statusChoices) {
		Main.statusChoices = statusChoices;
	}

	public static HashMap<Integer, Case> getCases() {
		return cases;
	}

	public static void setCases(HashMap<Integer, Case> cases) {
		Main.cases = cases;
	}

	public static Background getMenuBackground() {
		return menuBackground;
	}

	public static void setMenuBackground(Background menuBackground) {
		Main.menuBackground = menuBackground;
	}

	public static Updater getUpdater() {
		return updater;
	}

	public static void startUpdater() {
//		if (globalDebug)
//			logThis("Main.start() -- Main.startUpdater()");
		Task<Integer> updaterTask = new Task<Integer>() {

			@Override
			protected Integer call() throws Exception {
				updater = new Updater();
				return null;
			}

		};
		new Thread(updaterTask).run();
	}

	public static Image getBackgroundImage() {
		return backgroundImage;
	}

	public static void setBackgroundImage(Image image) {
		backgroundImage = image;
	}

	public static String getDefaultBackgroundLocation() {
		return defaultBackgroundLocation;
	}

	public static HashMap<Integer, Contact> getContacts() {
		return contacts;
	}

	public static void setContacts(HashMap<Integer, Contact> contacts) {
		Main.contacts = contacts;
	}

	public static HashMap<Integer, Facility> getFacilities() {
		return facilities;
	}

	public static void setFacilities(HashMap<Integer, Facility> facilities) {
		Main.facilities = facilities;
	}

	public static HashMap<Integer, Provider> getProviders() {
		return providers;
	}

	public static void setProviders(HashMap<Integer, Provider> providers) {
		Main.providers = providers;
	}

	public static HashMap<Integer, Organization> getOrganizations() {
		return organizations;
	}

	public static void setOrganizations(HashMap<Integer, Organization> organizations) {
		Main.organizations = organizations;
	}

	public static HashMap<Integer, Incident> getIncidents() {
		return incidents;
	}

	public static void setIncidents(HashMap<Integer, Incident> incidents) {
		Main.incidents = incidents;
	}

	public static LoadingScreen getLoading() {
		getController().hideScrollBars(true);
		return loading;
	}

	public static void setLoading(LoadingScreen loading) {
		Main.loading = loading;
	}

	public static void updateCases() {
		Task<Integer> tsk = new Task<Integer>() {

			@Override
			protected Integer call() throws Exception {
				setCases(Server.getCasesByOrganization(Main.getCurrentUser().getDefault_organization(), ConnectionResources.getConnection()));
				return null;
			}
		};
		new Thread(tsk).run();

	}

	public static int getCurrentCaseId() {
		return currentCaseId;
	}

	public static void setCurrentCaseId(int currentCaseId) {
		Main.currentCaseId = currentCaseId;
	}

	public static void addCase(int i, Case c) {
		cases.put(i, c);
	}

	public static void addIncident(int incidentId, Incident incidentToAdd) {
		incidents.put(incidentId, incidentToAdd);

	}

	public static void addContact(int contactId, Contact contactToAdd) {
		contacts.put(contactId, contactToAdd);
	}

	public static boolean isGlobalDebug() {
		return globalDebug;
	}

	public static double getVersion() {
		return version;
	}

	public static void setVersion(double version) {
		Main.version = version;
	}

	public static File getLog() {
		return log;
	}

	public static void setLog(File log) {
		Main.log = log;
	}

	public static void setGlobalDebug(boolean debug) {
		globalDebug = debug;
		if (globalDebug)
			setUpLogging();
	}

	private static void setUpLogging() {
		try {
			File file = new File(defaultLocation + System.getProperty("file.separator") + "Resources" + System.getProperty("file.separator") + "Logs");
			try {
				if (file.mkdir()) {
					System.out.println("Directory Created");
				}
			} catch (Exception e) {
				e.printStackTrace();
			}

			File newFile = new File(
					defaultLocation + System.getProperty("file.separator") + "Resources" + System.getProperty("file.separator") + "log-false.lk");

			File logFile = new File(
					defaultLocation + System.getProperty("file.separator") + "Resources" + System.getProperty("file.separator") + "log-true.lk");

			if (logFile.exists()) {
				globalDebug = true;
			} else {
				newFile.createNewFile();
			}

			if (globalDebug) {

				logName = "Log-" + LocalDate.now() + " " + LocalTime.now().getHour() + " " + LocalTime.now().getMinute() + " "
						+ LocalTime.now().getSecond();

				System.out.println("Logging location: " + defaultLocation + System.getProperty("file.separator") + "Resources"
						+ System.getProperty("file.separator") + "Logs" + System.getProperty("file.separator") + logName + ".txt");
				setLog(new File(defaultLocation + System.getProperty("file.separator") + "Resources" + System.getProperty("file.separator") + "Logs"
						+ System.getProperty("file.separator") + logName + ".txt"));
				if (!log.exists()) {

					log.createNewFile();

				}
				logThis("Main.setUpLogging() : Logging Started");
				System.out.println("Loggind Started");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void logThis(String logNote) {

		try {
			Files.write(Paths.get(Main.getLog().getAbsolutePath()), ("\n" + logNote).getBytes(),
					StandardOpenOption.APPEND);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void showTerminal() {
		System.out.println("Terminal Running");
		terminal = new Terminal();
		Parent root = terminal;
		Scene terminal = new Scene(root);
		Stage terminalStage = new Stage();
		terminalStage.setTitle("Terminal");
		terminalStage.setScene(terminal);
		terminalStage.show();
	}

	public static void sendTerminalMessage(String message) {
		if (terminal != null)
			terminal.sendMessage(message + "\n");
	}

}
