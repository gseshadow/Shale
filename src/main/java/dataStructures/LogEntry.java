package dataStructures;

import java.sql.Date;

import application.Main;

public class LogEntry {

	private int userId;
	private int objectTypeId;
	private int objectId;
	private String fieldName;
	private String stringValue;
	private Date dateValue;
	private boolean booleanValue;
	private int intValue;
	private String entryDate;
	private int fieldCode;

	public LogEntry() {

	}

	public LogEntry(int userId, int objectTypeId, int objectId, String fieldName, String stringValue, Date dateValue, boolean booleanValue, int intValue, String entryDate,
			int fieldCode) {
		this.userId = userId;
		this.objectTypeId = objectTypeId;
		this.objectId = objectId;
		this.fieldName = fieldName;
		this.stringValue = stringValue;
		this.dateValue = dateValue;
		this.booleanValue = booleanValue;
		this.intValue = intValue;
		this.entryDate = entryDate;
		this.fieldCode = fieldCode;

	}

	public int getUserId() {
		return userId;
	}

	public void setUserId(int userId) {
		this.userId = userId;
	}

	public int getObjectTypeId() {
		return objectTypeId;
	}

	public void setObjectTypeId(int objectTypeId) {
		this.objectTypeId = objectTypeId;
	}

	public int getObjectId() {
		return objectId;
	}

	public void setObjectId(int objectId) {
		this.objectId = objectId;
	}

	public String getFieldName() {
		return fieldName;
	}

	public void setFieldName(String fieldName) {
		this.fieldName = fieldName;
	}

	public String getStringValue() {
		return stringValue;
	}

	public void setStringValue(String stringValue) {
		this.stringValue = stringValue;
	}

	public Date getDateValue() {
		return dateValue;
	}

	public void setDateValue(Date dateValue) {
		this.dateValue = dateValue;
	}

	public boolean isBooleanValue() {
		return booleanValue;
	}

	public void setBooleanValue(boolean booleanValue) {
		this.booleanValue = booleanValue;
	}

	public int getIntValue() {
		return intValue;
	}

	public void setIntValue(int intValue) {
		this.intValue = intValue;
	}

	public String getEntryDate() {
		return entryDate;
	}

	public void setEntryDate(String entryDate) {
		this.entryDate = entryDate;
	}

	@Override
	public String toString() {
		String user = Main.getUsers().get(userId).getNameFull();
		/* ***************************************************/
		/* KEY for objectTypeId: */
		/* 0 = Organization */
		/* 1 = Facility */
		/* 2 = Provider */
		/* 3 = Contact */
		/* 4 = PracticeArea */
		/* 5 = Status */
		/* 6 = Incident */
		/* 7 = User */
		/* 8 = Case */
		/* **************************************************/
		String objType = "";
		int i = objectTypeId;
		String objectName = "";
		System.out.println("TEST CHANGE");// TODO
		switch (i) {
		case 0:
			objType = "Organization";
			break;
		case 1:
			objType = "Facility";
			break;
		case 2:
			objType = "Privider";
			break;
		case 3:
			objType = "Contact";
			if (Main.getContacts().get(objectId) != null)
				objectName = Main.getContacts().get(objectId).getNameFull();
			break;
		case 4:
			objType = "Practice Area";
			break;
		case 5:
			objType = "Status";
			break;
		case 6:
			objType = "Incident";
			break;
		case 7:
			objType = "User";
			if (Main.getUsers().get(objectId) != null)
				objectName = Main.getUsers().get(objectId).getNameFull();
			break;
		case 8:
			objType = "Case";
			if (Main.getCases().get(objectId) != null)
				objectName = Main.getCases().get(objectId).getCaseName();
			break;
		}
		/* ***************************************************/
		/* KEY for fieldCode: */
		/* 1 = boolean */
		/* 2 = int */
		/* 3 = Date */
		/* 4 = String */
		/* **************************************************/
		int f = fieldCode;
		switch (f) {
		case 1:
			return user + " changed " + objType + " - " + objectName + ": " + fieldName + " to " + booleanValue + " at: " + entryDate;
		case 2:
			return user + " changed " + objType + " - " + objectName + ": " + fieldName + " to " + intValue + " at: " + entryDate;
		case 3:
			return user + " changed " + objType + " - " + objectName + ": " + fieldName + " to " + dateValue + " at: " + entryDate;
		case 4:
			return user + " changed " + objType + " - " + objectName + ": " + fieldName + " to " + stringValue + " at: " + entryDate;
		}

		return "Error Processing Entry";
	}

}
