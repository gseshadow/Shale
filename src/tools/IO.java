package tools;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;

import GUIElements.IntakePane;
import application.Main;
import dataStructures.Case;
import dataStructures.Contact;
import dataStructures.Facility;
import dataStructures.Incident;
import dataStructures.Organization;
import dataStructures.PracticeArea;
import dataStructures.Provider;
import dataStructures.Status;
import dataStructures.User;

public class IO {
	private static String status = "";

	public static void saveCurrent(IntakePane intake) {

		Main.getUpdater().publish(Main.getCurrentUser().get_id() + "#" + "1" + "#" + String.valueOf(intake.getCase().get_id()) + "#");

		Case temp = intake.getCase();

		String file = temp.getFileName();// change to default location add

		temp.setFileName(file.toString());

		Main.setCase(temp);

		File directory = new File(temp.getPrefix());
		if (!directory.exists()) {
			try {
				if (directory.mkdir()) {
					System.out.println("Directory Created");
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		try {
			FileOutputStream fos = new FileOutputStream(file);

			try {
				ObjectOutputStream oos = new ObjectOutputStream(fos);

				oos.writeObject(temp);
				oos.close();

			} catch (IOException e) {
				e.printStackTrace();
			}

		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		}
		Main.getController().changeLeft(false);
	}

	public static void saveCurrent(Case temp, String newFileName) {
		System.out.println("SAVE CURRENT");
		Main.getUpdater().publish(Main.getCurrentUser().get_id() + "#" + "2" + "#" + String.valueOf(temp.get_id()) + "#");
//		String oldFileName = temp.getFileName();
		String oldFileName = newFileName;
		temp.setFileName(newFileName);

		Main.setCase(temp);

		try {
			FileOutputStream fos = new FileOutputStream(oldFileName);

			try {
				ObjectOutputStream oos = new ObjectOutputStream(fos);

				oos.writeObject(temp);
				oos.close();

			} catch (IOException e) {
				e.printStackTrace();
			}

		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		}

		File rename = new File(oldFileName);
		rename.renameTo(new File(newFileName));
		Main.getController().changeLeft(false);
	}

	public static void saveCurrent(Organization org, String fileLocation) {

//		Main.getUpdater().publish(Main.getCurrentUser().get_id() + "#" + "1" + "#" + String.valueOf(intake.getCase().get_id()) + "#");//TODO redo this

		File save = new File(fileLocation);
		File directory = new File(save.getParent());
		if (!directory.exists()) {
			try {
				if (directory.mkdir()) {
					System.out.println("Directory Created");
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		try {
			FileOutputStream fos = new FileOutputStream(fileLocation);

			try {
				ObjectOutputStream oos = new ObjectOutputStream(fos);

				oos.writeObject(org);
				oos.close();

			} catch (IOException e) {
				e.printStackTrace();
			}

		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		}
	}

	public static void saveCurrent(Facility facility, String fileLocation) {

//		Main.getUpdater().publish(Main.getCurrentUser().get_id() + "#" + "1" + "#" + String.valueOf(intake.getCase().get_id()) + "#");//TODO redo this

		File save = new File(fileLocation);
		File directory = new File(save.getParent());
		if (!directory.exists()) {
			try {
				if (directory.mkdir()) {
					System.out.println("Directory Created");
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		try {
			FileOutputStream fos = new FileOutputStream(fileLocation);

			try {
				ObjectOutputStream oos = new ObjectOutputStream(fos);

				oos.writeObject(facility);
				oos.close();

			} catch (IOException e) {
				e.printStackTrace();
			}

		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		}
	}

	public static void saveCurrent(Provider provider, String fileLocation) {

//		Main.getUpdater().publish(Main.getCurrentUser().get_id() + "#" + "1" + "#" + String.valueOf(intake.getCase().get_id()) + "#");//TODO redo this

		File save = new File(fileLocation);
		File directory = new File(save.getParent());
		if (!directory.exists()) {
			try {
				if (directory.mkdir()) {
					System.out.println("Directory Created");
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		try {
			FileOutputStream fos = new FileOutputStream(fileLocation);

			try {
				ObjectOutputStream oos = new ObjectOutputStream(fos);

				oos.writeObject(provider);
				oos.close();

			} catch (IOException e) {
				e.printStackTrace();
			}

		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		}
	}

	public static void saveCurrent(User user, String fileLocation) {

//		Main.getUpdater().publish(Main.getCurrentUser().get_id() + "#" + "1" + "#" + String.valueOf(intake.getCase().get_id()) + "#");//TODO redo this

		File save = new File(fileLocation);
		File directory = new File(save.getParent());
		if (!directory.exists()) {
			try {
				if (directory.mkdir()) {
					System.out.println("Directory Created");
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		try {
			FileOutputStream fos = new FileOutputStream(fileLocation);

			try {
				ObjectOutputStream oos = new ObjectOutputStream(fos);

				oos.writeObject(user);
				oos.close();

			} catch (IOException e) {
				e.printStackTrace();
			}

		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		}
	}

	public static void saveCurrent(PracticeArea practiceArea, String fileLocation) {

//		Main.getUpdater().publish(Main.getCurrentUser().get_id() + "#" + "1" + "#" + String.valueOf(intake.getCase().get_id()) + "#");//TODO redo this

		File save = new File(fileLocation);
		File directory = new File(save.getParent());
		if (!directory.exists()) {
			try {
				if (directory.mkdir()) {
					System.out.println("Directory Created");
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		try {
			FileOutputStream fos = new FileOutputStream(fileLocation);

			try {
				ObjectOutputStream oos = new ObjectOutputStream(fos);

				oos.writeObject(practiceArea);
				oos.close();

			} catch (IOException e) {
				e.printStackTrace();
			}

		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		}
	}

	public static void saveCurrent(Status status, String fileLocation) {

//		Main.getUpdater().publish(Main.getCurrentUser().get_id() + "#" + "1" + "#" + String.valueOf(intake.getCase().get_id()) + "#");//TODO redo this

		File save = new File(fileLocation);
		File directory = new File(save.getParent());
		if (!directory.exists()) {
			try {
				if (directory.mkdir()) {
					System.out.println("Directory Created");
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		try {
			FileOutputStream fos = new FileOutputStream(fileLocation);

			try {
				ObjectOutputStream oos = new ObjectOutputStream(fos);

				oos.writeObject(status);
				oos.close();

			} catch (IOException e) {
				e.printStackTrace();
			}

		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		}
	}

	public static void saveCurrent(Contact contact, String fileLocation) {

//		Main.getUpdater().publish(Main.getCurrentUser().get_id() + "#" + "1" + "#" + String.valueOf(intake.getCase().get_id()) + "#");//TODO redo this

		File save = new File(fileLocation);
		File directory = new File(save.getParent());
		if (!directory.exists()) {
			try {
				if (directory.mkdir()) {
					System.out.println("Directory Created");
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		try {
			FileOutputStream fos = new FileOutputStream(fileLocation);

			try {
				ObjectOutputStream oos = new ObjectOutputStream(fos);

				oos.writeObject(contact);
				oos.close();

			} catch (IOException e) {
				e.printStackTrace();
			}

		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		}
	}
	
	public static void saveCurrent(Incident incident, String fileLocation) {

//		Main.getUpdater().publish(Main.getCurrentUser().get_id() + "#" + "1" + "#" + String.valueOf(intake.getCase().get_id()) + "#");//TODO redo this

		File save = new File(fileLocation);
		File directory = new File(save.getParent());
		if (!directory.exists()) {
			try {
				if (directory.mkdir()) {
					System.out.println("Directory Created");
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		try {
			FileOutputStream fos = new FileOutputStream(fileLocation);

			try {
				ObjectOutputStream oos = new ObjectOutputStream(fos);

				oos.writeObject(incident);
				oos.close();

			} catch (IOException e) {
				e.printStackTrace();
			}

		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		}
	}

	public static void saveCurrent(Case saveCase, String fileLocation, int i) {

//		Main.getUpdater().publish(Main.getCurrentUser().get_id() + "#" + "1" + "#" + String.valueOf(intake.getCase().get_id()) + "#");//TODO redo this
		File save = new File(fileLocation);
		File directory = new File(save.getParent());
		if (!directory.exists()) {
			try {
				if (directory.mkdir()) {
					System.out.println("Directory Created");
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		try {

			FileOutputStream fos = new FileOutputStream(fileLocation);

			try {
				ObjectOutputStream oos = new ObjectOutputStream(fos);

				oos.writeObject(saveCase);
				oos.close();

			} catch (IOException e) {
				e.printStackTrace();
			}

		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		}
	}

	public static void saveCurrent(User user) {
//		Main.getUpdater().publish(Main.getCurrentUser().get_id() + "#" + "3" + "#" + String.valueOf(user.get_id()) + "#");

		try {
			FileOutputStream fos = new FileOutputStream(
					Main.getDefaultLocation() + System.getProperty("file.separator") + "Resources" + System.getProperty("file.separator") + "UserData");

			try {
				ObjectOutputStream oos = new ObjectOutputStream(fos);

				oos.writeObject(user);
				oos.close();
				status = "success";
			} catch (IOException e) {
				e.printStackTrace();
				status = e.getMessage();
			}

		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
			status = e1.getMessage();
		}

	}

	public static String getStatus() {
		return status;
	}

}
