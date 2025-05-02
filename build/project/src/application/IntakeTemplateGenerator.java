package application;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

import org.docx4j.Docx4J;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;

public class IntakeTemplateGenerator {

	public static String input_DOCX;

	public static String input_XML;

	public static String output_DOCX;

	public IntakeTemplateGenerator(Potential potential, File saveLocation) throws Exception {
		input_DOCX = Main.getFileLocation() + "\\IntakeResources\\" + "IntakeFormBlank.docx";
		input_XML = Main.getFileLocation() + "\\IntakeResources\\" + "IntakeTemplate.xml";

		output_DOCX = saveLocation.getAbsolutePath();
		System.out.println(input_DOCX);
		System.out.println(input_XML);
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
			statuteLim = potential.getIncidentMedNegDiscovered().toString();

		boolean run = true;
		if (run) {
			System.out.println("1 " + callDate);
			System.out.println("2 " + potential.getCallerTime());
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
			System.out.println("13 " + potential.getPracticeArea());
			System.out.println("14 " + medNegOcc);
			System.out.println("15 " + medNegDis);
			System.out.println("16 " + statuteLim);
			System.out.println("17 " + potential.getIncidentDoctorsInvolved());
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

		String xmlDoc = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + "<root>" //
				+ "	<caller>" //
				+ "		<callerDate>" + callDate + "</callerDate>" //
				+ "		<callerTime>"+ potential.getCallerTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
				+ "</callerTime>" //
				+ "		<callerPhone>" + potential.getCallerPhone() + "</callerPhone>" //
				+ "		<callerNameFirst>" + potential.getCallerNameFirst() + "</callerNameFirst>" //
				+ "		<callerNameLast>" + potential.getCallerNameLast() + "</callerNameLast>" //
				+ "	</caller>" //
				+ "	<client>" //
				+ "		<clientNameFirst>" + potential.getClientNameFirst() + "</clientNameFirst>" //
				+ "		<clientNameLast>" + potential.getClientNameLast() + "</clientNameLast>" //
				+ "		<clientPhone>" + potential.getClientPhone() + "</clientPhone>" //
				+ "		<clientAddress>" + potential.getClientAddress() + "</clientAddress>" //
				+ "		<clientEmail>" + potential.getClientEmail() + "</clientEmail>" //
				+ "		<clientDOB>" + clientDOB + "</clientDOB>" //
				+ "		<clientAge>" + clientAge + "</clientAge>" //
				+ "		<clientCondition>" + potential.getClientCondition() + "</clientCondition>" //
				+ "	</client>" //
				+ "	<incident>" //
				+ "		<practiceArea>" + potential.getPracticeArea() + "</practiceArea>" //
				+ "		<dateMedNegOccurred>" + medNegOcc + "</dateMedNegOccurred>" //
				+ "		<dateMedNegDiscovered>" + medNegDis + "</dateMedNegDiscovered>" //
				+ "		<dateStatuteOfLimitations>" + statuteLim + "</dateStatuteOfLimitations>" //
				+ "		<doctorsInvolved>" + potential.getIncidentDoctorsInvolved() + "</doctorsInvolved>" //
				+ "		<facilitiesInvolved>" + potential.getIncidentFacilitiesInvolved() + "</facilitiesInvolved>" //
				+ "		<medicalRecordsInHand>" + String.valueOf(potential.isIncidentMedRecsInHand())
				+ "</medicalRecordsInHand>" //
				+ "		<incidentSummary>" + potential.getIncidentSummary() + "</incidentSummary>" //
				+ "		<updates>" + potential.getIncidentUpdates() + "</updates>" //
				+ "	</incident>" //
				+ "	<followUp>" //
				+ "		<questionsForPatient>" + potential.getFollowUpQuestionsForPatient() + "</questionsForPatient>" //
				+ "		<meetingWithClient>" + String.valueOf(potential.isFollowUpMeetingWithClient())
				+ "</meetingWithClient>" //
				+ "		<nurseReview>" + String.valueOf(potential.isFollowUpNurseReview()) + "</nurseReview>" //
				+ "		<doctorReview>" + String.valueOf(potential.isFollowUpDoctorReview()) + "</doctorReview>" //
				+ "	</followUp>" //
				+ "	<accepted>" //
				+ "		<chronology>" + String.valueOf(potential.isAcceptedChronology()) + "</chronology>" //
				+ "		<consultantExpertSearch>" + String.valueOf(potential.isAcceptedConsultantExpertSearch())
				+ "</consultantExpertSearch>" //
				+ "		<testifyingExpertSearch>" + String.valueOf(potential.isAcceptedTestifyingExpertSearch())
				+ "</testifyingExpertSearch>" //
				+ "		<supportiveMedicalLiterature>"
				+ String.valueOf(potential.isAcceptedSupportiveMedicalLiterature()) + "</supportiveMedicalLiterature>" //
				+ "		<details>" + potential.getAcceptedDetail() + "</details>" //
				+ "	</accepted>" //
				+ "	<rejected>" //
				+ "		<chronology>" + String.valueOf(potential.isDeniedChronology()) + "</chronology>" //
				+ "		<details>" + potential.getDeniedDetails() + "</details>" //
				+ "	</rejected>" //
				+ "</root>"; //
		BufferedWriter write;
		try {
			FileWriter fw = new FileWriter(Main.getFileLocation() + "\\IntakeResources\\" + "IntakeTemplate.xml");
			write = new BufferedWriter(fw);
			write.write(xmlDoc);
			write.close();
		} catch (IOException e1) {
			e1.printStackTrace();
		}

		//////////////////////////////////////////////////////

		/* Generate docx intake form using xml data created above */
		WordprocessingMLPackage wordMLPackage = Docx4J.load(new File(input_DOCX));
		FileInputStream xmlStream = new FileInputStream(new File(input_XML));
		Docx4J.bind(wordMLPackage, xmlStream,
				Docx4J.FLAG_BIND_INSERT_XML | Docx4J.FLAG_BIND_BIND_XML | Docx4J.FLAG_BIND_REMOVE_SDT);
		Docx4J.save(wordMLPackage, new File(output_DOCX), Docx4J.FLAG_NONE);
	}

}
