package tools;

import java.io.*;
import java.time.LocalDate;
import java.util.ArrayList;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import dataStructures.Case;
import dataStructures.CaseBuilder;

public class PotentiaListGenerator {
	String message = "";

	public PotentiaListGenerator(ArrayList<Case> cases, File file) {

		// Creating Workbook instances
		Workbook workbook = new XSSFWorkbook();

		// Create header font
		Font headerFont = workbook.createFont();
		headerFont.setBold(true);
		headerFont.setFontHeightInPoints((short) 12);
		headerFont.setColor(IndexedColors.BLACK.index);

		// Create sol font
		Font solFont = workbook.createFont();
		solFont.setBold(true);
		solFont.setColor(IndexedColors.RED.index);

		// Create header CellStyle
		CellStyle headerStyle = workbook.createCellStyle();
		headerStyle.setFont(headerFont);
		headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
		headerStyle.setFillForegroundColor(IndexedColors.SKY_BLUE.index);
		headerStyle.setBorderBottom(BorderStyle.THIN);
		headerStyle.setBorderTop(BorderStyle.THIN);
		headerStyle.setBorderLeft(BorderStyle.THIN);
		headerStyle.setBorderRight(BorderStyle.THIN);

		// Create General CellStyle
		CellStyle generalStyle = workbook.createCellStyle();
		generalStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
		generalStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.index);
		generalStyle.setBorderBottom(BorderStyle.THIN);
		generalStyle.setBorderTop(BorderStyle.THIN);
		generalStyle.setBorderLeft(BorderStyle.THIN);
		generalStyle.setBorderRight(BorderStyle.THIN);

		// Create attorney CellStyle
		CellStyle attorneyStyle = workbook.createCellStyle();
		attorneyStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
		attorneyStyle.setFillForegroundColor(IndexedColors.WHITE.index);
		attorneyStyle.setBorderBottom(BorderStyle.THIN);
		attorneyStyle.setBorderTop(BorderStyle.THIN);
		attorneyStyle.setBorderLeft(BorderStyle.THIN);
		attorneyStyle.setBorderRight(BorderStyle.THIN);

		// Create andre CellStyle
		CellStyle andreStyle = workbook.createCellStyle();
		andreStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
		andreStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.index);
		andreStyle.setBorderBottom(BorderStyle.THIN);
		andreStyle.setBorderTop(BorderStyle.THIN);
		andreStyle.setBorderLeft(BorderStyle.THIN);
		andreStyle.setBorderRight(BorderStyle.THIN);

		// Create lisa CellStyle
		CellStyle lisaStyle = workbook.createCellStyle();
		lisaStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
		lisaStyle.setFillForegroundColor(IndexedColors.RED.index);
		lisaStyle.setBorderBottom(BorderStyle.THIN);
		lisaStyle.setBorderTop(BorderStyle.THIN);
		lisaStyle.setBorderLeft(BorderStyle.THIN);
		lisaStyle.setBorderRight(BorderStyle.THIN);

		// Create luke CellStyle
		CellStyle lukeStyle = workbook.createCellStyle();
		lukeStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
		lukeStyle.setFillForegroundColor(IndexedColors.LIGHT_ORANGE.index);
		lukeStyle.setBorderBottom(BorderStyle.THIN);
		lukeStyle.setBorderTop(BorderStyle.THIN);
		lukeStyle.setBorderLeft(BorderStyle.THIN);
		lukeStyle.setBorderRight(BorderStyle.THIN);

		// Create melanie CellStyle
		CellStyle melanieStyle = workbook.createCellStyle();
		melanieStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
		melanieStyle.setFillForegroundColor(IndexedColors.VIOLET.index);
		melanieStyle.setBorderBottom(BorderStyle.THIN);
		melanieStyle.setBorderTop(BorderStyle.THIN);
		melanieStyle.setBorderLeft(BorderStyle.THIN);
		melanieStyle.setBorderRight(BorderStyle.THIN);

		// Create SOL CellStyle
		CellStyle solStyle = workbook.createCellStyle();
		solStyle.setFont(solFont);
		solStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
		solStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.index);
		solStyle.setBorderBottom(BorderStyle.THIN);
		solStyle.setBorderTop(BorderStyle.THIN);
		solStyle.setBorderLeft(BorderStyle.THIN);
		solStyle.setBorderRight(BorderStyle.THIN);

		// Create SOL CellStyle
		CellStyle solSoonStyle = workbook.createCellStyle();
		solSoonStyle.setFont(solFont);
		solSoonStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
		solSoonStyle.setFillForegroundColor(IndexedColors.YELLOW.index);
		solSoonStyle.setBorderBottom(BorderStyle.THIN);
		solSoonStyle.setBorderTop(BorderStyle.THIN);
		solSoonStyle.setBorderLeft(BorderStyle.THIN);
		solSoonStyle.setBorderRight(BorderStyle.THIN);

		// Now creating Sheets using sheet object
		Sheet sheet = workbook.createSheet("Potentials");

		// Create the header row style and content
		Row header = sheet.createRow(0);

		String[] headerString = { "Last Name", "First Name", "Date of Intake", "Defendants", "Description", "Status", "SOL", "Attorney", "Code" };

		for (int i = 0; i < headerString.length; i++) {
			Cell cell = header.createCell(i);
			cell.setCellValue(headerString[i]);
			cell.setCellStyle(headerStyle);

		}

		int rowValue = 1;

		for (Case cse : cases) {
			cse = CaseBuilder.build(cse);

			int col = 0;
			Row row = sheet.createRow(rowValue++);
			Cell lastName = row.createCell(col++);
			lastName.setCellStyle(generalStyle);
			lastName.setCellValue(cse.getClientNameLast());

			Cell firstName = row.createCell(col++);
			firstName.setCellStyle(generalStyle);
			firstName.setCellValue(cse.getClientNameFirst());

			Cell callerDate = row.createCell(col++);
			callerDate.setCellStyle(generalStyle);
			callerDate.setCellValue(cse.getCallerDateString());

			Cell defendants = row.createCell(col++);
			defendants.setCellStyle(generalStyle);
			defendants.setCellValue(cse.getIncidentPotentialDefendants());

			Cell description = row.createCell(col++);
			description.setCellStyle(generalStyle);
			description.setCellValue(cse.getIncidentDescription());

			Cell status = row.createCell(col++);
			status.setCellStyle(generalStyle);
			status.setCellValue(cse.getIncidentCaseStatus());

			{
				Cell statuteLim = row.createCell(col++);

				if (cse.getIncidentStatuteOfLimitations() != null) {
					System.out.println("Has SOL");// TODO
					if (LocalDate.now().isAfter(cse.getIncidentStatuteOfLimitations().minusMonths(2))) {
						System.out.println("SOON");// TODO
						statuteLim.setCellStyle(solSoonStyle);
					} else {
						System.out.println("LATER");// TODO
						statuteLim.setCellStyle(solStyle);
					}
				} else {
					System.out.println("No SOL");// TODO
					statuteLim.setCellStyle(generalStyle);
				}
				System.out.println("");// TODO
				statuteLim.setCellValue(cse.getIncidentStatuteOfLimitationsString());
			}

			{

				Cell attorney = row.createCell(col++);
				if (cse.getOfficeResponsibleAttorney() != null) {

					switch (cse.getOfficeResponsibleAttorney().getInitials()) {

					case "AKA": {
						attorney.setCellStyle(andreStyle);
						break;
					}
					case "LKC": {
						attorney.setCellStyle(lisaStyle);
						break;
					}
					case "LWH": {
						attorney.setCellStyle(lukeStyle);
						break;
					}
					case "MLB": {
						attorney.setCellStyle(melanieStyle);
						break;
					}
					default:
					}
					attorney.setCellValue(cse.getOfficeResponsibleAttorney().getInitials());
				} else
					attorney.setCellValue("");

			}

			if (!cse.getOfficePrinterCode().equals("")) {
				Cell code = row.createCell(col++);
				code.setCellStyle(generalStyle);
				code.setCellValue(Integer.valueOf(cse.getOfficePrinterCode()));
			} else {
				Cell code = row.createCell(col++);
				code.setCellStyle(generalStyle);
				code.setCellValue(cse.getOfficePrinterCode());
			}
		}

		// Auto size columns
		for (int i = 0; i < headerString.length; i++) {
			sheet.autoSizeColumn(i);
		}

		// An output stream accepts output bytes and
		// sends them to sink
		try {
			FileOutputStream fileOut = new FileOutputStream(file.getAbsolutePath());
			workbook.write(fileOut);
			fileOut.close();
			workbook.close();
			message = "List Created Successfully";

		} catch (FileNotFoundException e) {
			e.printStackTrace();
			message = "ERROR - Failed to Create List";
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	public String getMessage() {
		return message;
	}

}
