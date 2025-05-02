package dataStructures;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import application.Main;

public class CaseBuilder {

	public CaseBuilder() {

	}

	public static Case build(Case c) {

		if (c.isSameAsCaller()) {
		}
		c.setClient(Main.getContacts().get(c.getClientId()));
		c.setCaller(Main.getContacts().get(c.getCallerId()));
		c.setOfficeResponsibleAttorney(Main.getUsers().get(c.getOfficeResponsibleAttorneyId()));
		c.setOfficeIntakePerson(Main.getUsers().get(c.getOfficeIntakePersonId()));
		c.setCasePracticeArea(Main.getPracticeAreas().get(c.getCasePracticeAreaId()));
		c.setCaseStatus(Main.getStatusChoices().get(c.getCaseStatusId()));
		c.setCaseOpposingCounsel(Main.getContacts().get(c.getCaseOpposingCounselId()));
		c.setCaseOrganization(Main.getOrganizations().get(c.getCaseOrganizationId()));
		c.setCaseJudge(Main.getContacts().get(c.getCaseJudgeId()));
		c.setIncident(Main.getIncidents().get(c.getIncidentId()));

		if (Main.isGlobalDebug()) {
			System.out.println("************************************");
			System.out.println("CaseBuilder()");
			System.out.println("Client id: " + c.getClientId());
			System.out.println("Caller id: " + c.getCallerId());
			System.out.println("Incident id: " + c.getIncidentId());
			System.out.println("Organization id: " + c.getCaseOrganizationId());
			System.out.println("************************************");
			try {
				Files.write(Paths.get(Main.getLog().getAbsolutePath()), "\nCaseBuilder".getBytes(),
						StandardOpenOption.APPEND);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		return c;
	}

}
