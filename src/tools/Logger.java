package tools;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import application.Main;

public class Logger {

	public static void append(String s) {
		{
			CharSequence charSequence = s;
			File log = Main.getLog();
			try {
				FileWriter fileWriter;

				fileWriter = new FileWriter(log.getAbsoluteFile(), false);

				BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
				bufferedWriter.append(charSequence);
				bufferedWriter.close();

			} catch (IOException e) {
				e.printStackTrace();
			}
		}

	}
}
