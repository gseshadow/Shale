package tools;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;

import org.apache.poi.xwpf.usermodel.XWPFDocument;

import application.Main;
import dataStructures.Case;
import pl.jsolve.templ4docx.core.Docx;
import pl.jsolve.templ4docx.core.VariablePattern;
import pl.jsolve.templ4docx.variable.TextVariable;
import pl.jsolve.templ4docx.variable.Variables;

public class IntakeTemplateGenerator {
	public IntakeTemplateGenerator(Case potential, File saveLocation) {
//		if (Main.isGlobalDebug())
		Main.sendTerminalMessage("IntakeTemplateGenerator()");// TODO
		System.out.println("IntakeTemplateGenerator()");

		String clientDOB = "";
		String clientAge = "";
		if (potential.getClientDOB() != null) {
			clientDOB = potential.getClientDOB().toString();
			clientAge = String.valueOf(potential.getClientAge());
		}

		String callDate = "";
		if (potential.getCallerDate() != null)
			callDate = potential.getCallerDate().toString();

		String medNegOcc = "";
		if (potential.getIncidentMedNegOccurred() != null)
			medNegOcc = potential.getIncidentMedNegOccurred().toString();

		String medNegDis = "";
		if (potential.getIncidentMedNegDiscovered() != null)
			medNegDis = potential.getIncidentMedNegDiscovered().toString();

		String statuteLim = "";
		if (potential.getIncidentStatuteOfLimitations() != null)
			statuteLim = potential.getIncidentStatuteOfLimitations().toString();

		String callerTime = "";
		String ampm = " AM";

		int hour = potential.getCallerTime().getHour();
		if (hour > 12) {
			ampm = " PM";
			hour -= 12;
		}
		callerTime = (hour + ":" + potential.getCallerTime().getMinute() / 10 + (potential.getCallerTime().getMinute() % 10) + ampm);

		{
			boolean run = true;
			if (run) {
				Main.sendTerminalMessage("if(run)...");// TODO
				System.out.println("1 " + callDate);
				System.out.println("2 " + callerTime);
				System.out.println("3 " + potential.getCallerNameFirst());
				System.out.println("4 " + potential.getCallerNameLast());
				System.out.println("5 " + potential.getClientNameFirst());
				System.out.println("6 " + potential.getClientNameLast());
				System.out.println("7 " + potential.getClientPhone());
				System.out.println("8 " + potential.getClientAddress());
				System.out.println("9 " + potential.getClientEmail());
				System.out.println("10 " + clientDOB);
				System.out.println("11 " + clientAge);
				System.out.println("12 " + potential.getClientCondition());
				System.out.println("13 " + potential.getCasePracticeArea().getPracticeArea());
				System.out.println("14 " + medNegOcc);
				System.out.println("15 " + medNegDis);
				System.out.println("16 " + statuteLim);
				System.out.println("17 " + potential.getPotentialDefendants());
				System.out.println("18 " + potential.getIncidentFacilitiesInvolved());
				System.out.println("19 " + String.valueOf(potential.isIncidentMedRecsInHand()));
				System.out.println("20 " + potential.getIncidentSummary());
				System.out.println("21 " + potential.getIncidentUpdates());
				System.out.println("22 " + potential.getFollowUpQuestionsForPatient());
				System.out.println("23 " + String.valueOf(potential.isFollowUpMeetingWithClient()));
				System.out.println("24 " + String.valueOf(potential.isFollowUpNurseReview()));
				System.out.println("25 " + String.valueOf(potential.isFollowUpDoctorReview()));
				System.out.println("26 " + String.valueOf(potential.isAcceptedChronology()));
				System.out.println("27 " + String.valueOf(potential.isAcceptedConsultantExpertSearch()));
				System.out.println("28 " + String.valueOf(potential.isAcceptedTestifyingExpertSearch()));
				System.out.println("29 " + String.valueOf(potential.isAcceptedSupportiveMedicalLiterature()));
				System.out.println("30 " + potential.getAcceptedDetail());
				System.out.println("31 " + String.valueOf(potential.isDeniedChronology()));
				System.out.println("32 " + potential.getDeniedDetails());
			}
			if (Main.isGlobalDebug()) {
				Main.sendTerminalMessage("GlobalDebug is on....");// TODO
				try {
//					Files.write(Paths.get(Main.getLog().getAbsolutePath()), .getBytes(), StandardOpenOption.APPEND);

					Files.write(Paths.get(Main.getLog().getAbsolutePath()), ("1 " + callDate).getBytes(), StandardOpenOption.APPEND);
					Files.write(Paths.get(Main.getLog().getAbsolutePath()), ("2 " + callerTime).getBytes(), StandardOpenOption.APPEND);
					Files.write(Paths.get(Main.getLog().getAbsolutePath()), ("3 " + potential.getCallerNameFirst()).getBytes(), StandardOpenOption.APPEND);
					Files.write(Paths.get(Main.getLog().getAbsolutePath()), ("4 " + potential.getCallerNameLast()).getBytes(), StandardOpenOption.APPEND);
					Files.write(Paths.get(Main.getLog().getAbsolutePath()), ("5 " + potential.getClientNameFirst()).getBytes(), StandardOpenOption.APPEND);
					Files.write(Paths.get(Main.getLog().getAbsolutePath()), ("6 " + potential.getClientNameLast()).getBytes(), StandardOpenOption.APPEND);
					Files.write(Paths.get(Main.getLog().getAbsolutePath()), ("7 " + potential.getClientPhone()).getBytes(), StandardOpenOption.APPEND);
					Files.write(Paths.get(Main.getLog().getAbsolutePath()), ("8 " + potential.getClientAddress()).getBytes(), StandardOpenOption.APPEND);
					Files.write(Paths.get(Main.getLog().getAbsolutePath()), ("9 " + potential.getClientEmail()).getBytes(), StandardOpenOption.APPEND);
					Files.write(Paths.get(Main.getLog().getAbsolutePath()), ("10 " + clientDOB).getBytes(), StandardOpenOption.APPEND);
					Files.write(Paths.get(Main.getLog().getAbsolutePath()), ("11 " + clientAge).getBytes(), StandardOpenOption.APPEND);
					Files.write(Paths.get(Main.getLog().getAbsolutePath()), ("12 " + potential.getClientCondition()).getBytes(), StandardOpenOption.APPEND);
					Files.write(Paths.get(Main.getLog().getAbsolutePath()), ("13 " + potential.getCasePracticeArea().getPracticeArea()).getBytes(), StandardOpenOption.APPEND);
					Files.write(Paths.get(Main.getLog().getAbsolutePath()), ("14 " + medNegOcc).getBytes(), StandardOpenOption.APPEND);
					Files.write(Paths.get(Main.getLog().getAbsolutePath()), ("15 " + medNegDis).getBytes(), StandardOpenOption.APPEND);
					Files.write(Paths.get(Main.getLog().getAbsolutePath()), ("16 " + statuteLim).getBytes(), StandardOpenOption.APPEND);
					Files.write(Paths.get(Main.getLog().getAbsolutePath()), ("17 " + potential.getPotentialDefendants()).getBytes(), StandardOpenOption.APPEND);
					Files.write(Paths.get(Main.getLog().getAbsolutePath()), ("18 " + potential.getIncidentFacilitiesInvolved()).getBytes(), StandardOpenOption.APPEND);
					Files.write(Paths.get(Main.getLog().getAbsolutePath()), ("19 " + String.valueOf(potential.isIncidentMedRecsInHand())).getBytes(), StandardOpenOption.APPEND);
					Files.write(Paths.get(Main.getLog().getAbsolutePath()), ("20 " + potential.getIncidentSummary()).getBytes(), StandardOpenOption.APPEND);
					Files.write(Paths.get(Main.getLog().getAbsolutePath()), ("21 " + potential.getIncidentUpdates()).getBytes(), StandardOpenOption.APPEND);
					Files.write(Paths.get(Main.getLog().getAbsolutePath()), ("22 " + potential.getFollowUpQuestionsForPatient()).getBytes(), StandardOpenOption.APPEND);
					Files.write(Paths.get(Main.getLog().getAbsolutePath()), ("23 " + String.valueOf(potential.isFollowUpMeetingWithClient())).getBytes(),
							StandardOpenOption.APPEND);
					Files.write(Paths.get(Main.getLog().getAbsolutePath()), ("24 " + String.valueOf(potential.isFollowUpNurseReview())).getBytes(), StandardOpenOption.APPEND);
					Files.write(Paths.get(Main.getLog().getAbsolutePath()), ("25 " + String.valueOf(potential.isFollowUpDoctorReview())).getBytes(), StandardOpenOption.APPEND);
					Files.write(Paths.get(Main.getLog().getAbsolutePath()), ("26 " + String.valueOf(potential.isAcceptedChronology())).getBytes(), StandardOpenOption.APPEND);
					Files.write(Paths.get(Main.getLog().getAbsolutePath()), ("27 " + String.valueOf(potential.isAcceptedConsultantExpertSearch())).getBytes(),
							StandardOpenOption.APPEND);
					Files.write(Paths.get(Main.getLog().getAbsolutePath()), ("28 " + String.valueOf(potential.isAcceptedTestifyingExpertSearch())).getBytes(),
							StandardOpenOption.APPEND);
					Files.write(Paths.get(Main.getLog().getAbsolutePath()), ("29 " + String.valueOf(potential.isAcceptedSupportiveMedicalLiterature())).getBytes(),
							StandardOpenOption.APPEND);
					Files.write(Paths.get(Main.getLog().getAbsolutePath()), ("30 " + potential.getAcceptedDetail()).getBytes(), StandardOpenOption.APPEND);
					Files.write(Paths.get(Main.getLog().getAbsolutePath()), ("31 " + String.valueOf(potential.isDeniedChronology())).getBytes(), StandardOpenOption.APPEND);
					Files.write(Paths.get(Main.getLog().getAbsolutePath()), ("32 " + potential.getDeniedDetails()).getBytes(), StandardOpenOption.APPEND);

				} catch (IOException e) {
					Main.sendTerminalMessage("Catch 1 - " + e.getMessage());// TODO
					e.printStackTrace();
				}
			}

			// create new instance of docx template

			Docx blankForm = new Docx(Main.getDefaultLocation() + System.getProperty("file.separator") + "Resources"
					+ System.getProperty("file.separator") + "IntakeFormBlank.docx");

			// set the variable pattern. In this example the pattern is as follows:
			// #{variableName}
			blankForm.setVariablePattern(new VariablePattern("#{", "}"));

			// read docx content as simple text
			String content = blankForm.readTextContent();

			// and display it
			System.out.println(content);

			// find all variables satisfying the pattern #{...}
			List<String> findVariables = blankForm.findVariables();

			// and display it
			for (String var : findVariables) {
				System.out.println("VARIABLE => " + var);
			}

			// prepare map of variables for template
			Variables var = new Variables();
			var.addTextVariable(new TextVariable("#{practiceArea}", potential.getCasePracticeArea().getPracticeArea()));
			var.addTextVariable(new TextVariable("#{callerDate}", callDate));
			var.addTextVariable(new TextVariable("#{callerTime}", callerTime));
			var.addTextVariable(new TextVariable("#{callerNameLast}", potential.getCallerNameLast()));
			var.addTextVariable(new TextVariable("#{callerNameFirst}", potential.getCallerNameFirst()));
			var.addTextVariable(new TextVariable("#{callerPhone}", potential.getCallerPhone()));
			var.addTextVariable(new TextVariable("#{clientNameLast}", potential.getClientNameLast()));
			var.addTextVariable(new TextVariable("#{clientNameFirst}", potential.getClientNameFirst()));
			var.addTextVariable(new TextVariable("#{clientEmail}", potential.getClientEmail()));
			var.addTextVariable(new TextVariable("#{clientPhone}", potential.getClientPhone()));
			var.addTextVariable(new TextVariable("#{clientAddress}", potential.getClientAddress()));
			var.addTextVariable(new TextVariable("#{clientDOB}", clientDOB));
			var.addTextVariable(new TextVariable("#{clientAge}", clientAge));
			var.addTextVariable(new TextVariable("#{clientCondition}", potential.getClientCondition()));
			var.addTextVariable(new TextVariable("#{incidentDateMedNegDiscovered}", medNegDis));
			var.addTextVariable(new TextVariable("#{incidentDateMedNegOccurred}", medNegOcc));
			var.addTextVariable(new TextVariable("#{incidentDoctorsInvolved}", potential.getPotentialDefendants()));
			var.addTextVariable(new TextVariable("#{incidentFacilitiesInvolved}", potential.getIncidentFacilitiesInvolved()));
			var.addTextVariable(new TextVariable("#{incidentDateOfStatuteOfLimitations}", statuteLim));
			var.addTextVariable(new TextVariable("#{incidentMedicalRecordsInHand}", String.valueOf(potential.isIncidentMedRecsInHand())));
			var.addTextVariable(new TextVariable("#{incidentSummary}", potential.getIncidentSummary()));
			var.addTextVariable(new TextVariable("#{incidentUpdates}", potential.getIncidentUpdates()));

			// fill template by given map of variables
			blankForm.fillTemplate(var);
			content = blankForm.readTextContent();

			// and display it
			if (Main.isGlobalDebug()) {
				Main.sendTerminalMessage("Display it...");// TODO
				try {
					Files.write(Paths.get(Main.getLog().getAbsolutePath()), content.getBytes(), StandardOpenOption.APPEND);
				} catch (IOException e1) {
					if (Main.isGlobalDebug())
						try {
							Files.write(Paths.get(Main.getLog().getAbsolutePath()), e1.getMessage().getBytes(), StandardOpenOption.APPEND);
						} catch (IOException e) {
							Main.sendTerminalMessage("Catch 2 - " + e.getMessage());// TODO
							e.printStackTrace();
						}
					Main.sendTerminalMessage("Catch 3 - " + e1.getMessage());// TODO
					e1.printStackTrace();
				}
			}
			System.out.println(content);
			// save filled document
			blankForm.save(Main.getDefaultLocation() + System.getProperty("file.separator") + "Resources" + System.getProperty("file.separator")
					+ "IntakeFormFilled.docx");
			XWPFDocument doc = blankForm.getXWPFDocument();
			try {
				FileOutputStream fos = new FileOutputStream(saveLocation);
				doc.write(fos);
				fos.close();
			} catch (FileNotFoundException e) {
				if (Main.isGlobalDebug())
					try {
						Files.write(Paths.get(Main.getLog().getAbsolutePath()), e.getMessage().getBytes(), StandardOpenOption.APPEND);
					} catch (IOException e1) {
						Main.sendTerminalMessage("Catch 4 - " + e1.getMessage());// TODO
						e1.printStackTrace();
					}
				Main.sendTerminalMessage("Catch 5 - " + e.getMessage());// TODO
				e.printStackTrace();
			} catch (IOException e) {
				if (Main.isGlobalDebug())
					try {
						Files.write(Paths.get(Main.getLog().getAbsolutePath()), e.getMessage().getBytes(), StandardOpenOption.APPEND);
					} catch (IOException e1) {
						Main.sendTerminalMessage("Catch 6 - " + e1.getMessage());// TODO
						e1.printStackTrace();
					}
				Main.sendTerminalMessage("Catch 7 - " + e.getMessage());// TODO
				e.printStackTrace();
			}
		}
	}
}
