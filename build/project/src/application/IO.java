package application;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;

public class IO {

	public static void saveCurrent(Intake intake) {

		Potential temp = intake.getPotential();

		String file = temp.getFileName();

		temp.setFileName(file.toString());

		Main.setPotential(temp);

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

	public static void saveCurrent(Potential temp, String newFileName) {

		String oldFileName = temp.getFileName();

		temp.setFileName(newFileName);

		Main.setPotential(temp);

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

}
