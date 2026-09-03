package com.shale.data.dao;

import com.shale.core.dto.CaseDetailDto;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Objects;

/** Builds the PHI-safe Case chronology for the authoritative Details mutation. */
final class CaseDetailsTimelineWriter {
    private CaseDetailsTimelineWriter() {}

    static void appendChanges(Connection con, long caseId, int tenant, int actor,
            CaseDetailDto before, CaseDetailDto after) throws SQLException {
        text(con,caseId,tenant,actor,CaseDao.CaseTimelineEventTypes.CASE_NAME_CHANGED,"changed Case Name",before.getCaseName(),after.getCaseName(),false);
        text(con,caseId,tenant,actor,CaseDao.CaseTimelineEventTypes.CASE_NUMBER_CHANGED,"changed Case Number",before.getCaseNumber(),after.getCaseNumber(),false);
        text(con,caseId,tenant,actor,CaseDao.CaseTimelineEventTypes.DESCRIPTION_CHANGED,"updated Description",before.getDescription(),after.getDescription(),true);
        date(con,caseId,tenant,actor,CaseDao.CaseTimelineEventTypes.ACCEPTED_DATE_CHANGED,"Accepted date changed",before.getAcceptedDate(),after.getAcceptedDate());
        date(con,caseId,tenant,actor,CaseDao.CaseTimelineEventTypes.CLOSED_DATE_CHANGED,"Closed date changed",before.getClosedDate(),after.getClosedDate());
        date(con,caseId,tenant,actor,CaseDao.CaseTimelineEventTypes.DENIED_DATE_CHANGED,"Denied date changed",before.getDeniedDate(),after.getDeniedDate());
        bool(con,caseId,tenant,actor,CaseDao.CaseTimelineEventTypes.ESTATE_CASE_CHANGED,"Estate case updated",nullableBoolean(before.getClientEstate()),nullableBoolean(after.getClientEstate()));
        text(con,caseId,tenant,actor,CaseDao.CaseTimelineEventTypes.OFFICE_CASE_CODE_CHANGED,"Office case code changed",before.getOfficePrinterCode(),after.getOfficePrinterCode(),false);
        bool(con,caseId,tenant,actor,CaseDao.CaseTimelineEventTypes.MEDICAL_RECORDS_REQUESTED_CHANGED,"Medical records requested updated",before.getMedicalRecordsRequested(),after.getMedicalRecordsRequested());
        bool(con,caseId,tenant,actor,CaseDao.CaseTimelineEventTypes.FEE_AGREEMENT_SIGNED_CHANGED,"Fee agreement signed updated",before.getFeeAgreementSigned(),after.getFeeAgreementSigned());
        bool(con,caseId,tenant,actor,CaseDao.CaseTimelineEventTypes.NON_ENGAGEMENT_LETTER_SENT_CHANGED,"Non-engagement letter sent updated",before.getNonEngagementLetterSent(),after.getNonEngagementLetterSent());
        bool(con,caseId,tenant,actor,CaseDao.CaseTimelineEventTypes.ACCEPTED_CHRONOLOGY_CHANGED,"Accepted chronology updated",before.getAcceptedChronology(),after.getAcceptedChronology());
        bool(con,caseId,tenant,actor,CaseDao.CaseTimelineEventTypes.CONSULTANT_EXPERT_SEARCH_CHANGED,"Consultant expert search updated",before.getAcceptedConsultantExpertSearch(),after.getAcceptedConsultantExpertSearch());
        bool(con,caseId,tenant,actor,CaseDao.CaseTimelineEventTypes.TESTIFYING_EXPERT_SEARCH_CHANGED,"Testifying expert search updated",before.getAcceptedTestifyingExpertSearch(),after.getAcceptedTestifyingExpertSearch());
        bool(con,caseId,tenant,actor,CaseDao.CaseTimelineEventTypes.MEDICAL_LITERATURE_CHANGED,"Medical literature updated",before.getAcceptedMedicalLiterature(),after.getAcceptedMedicalLiterature());
        text(con,caseId,tenant,actor,CaseDao.CaseTimelineEventTypes.ACCEPTED_DETAIL_UPDATED,"Accepted detail updated",before.getAcceptedDetail(),after.getAcceptedDetail(),true);
        bool(con,caseId,tenant,actor,CaseDao.CaseTimelineEventTypes.DENIED_CHRONOLOGY_CHANGED,"Denied chronology updated",before.getDeniedChronology(),after.getDeniedChronology());
        text(con,caseId,tenant,actor,CaseDao.CaseTimelineEventTypes.DENIED_DETAIL_UPDATED,"Denied detail updated",before.getDeniedDetail(),after.getDeniedDetail(),true);
        text(con,caseId,tenant,actor,CaseDao.CaseTimelineEventTypes.SUMMARY_UPDATED,"Summary updated",before.getSummary(),after.getSummary(),true);
        bool(con,caseId,tenant,actor,CaseDao.CaseTimelineEventTypes.RECEIVED_UPDATES_CHANGED,"Received updates updated",nullableBoolean(before.getReceivedUpdates()),nullableBoolean(after.getReceivedUpdates()));
        if (!Objects.equals(before.getPracticeAreaId(),after.getPracticeAreaId())) {
            CaseTimelineWriter.append(con,caseId,tenant,actor,CaseDao.CaseTimelineEventTypes.PRACTICE_AREA_CHANGED,
                    "Practice area changed","from "+practiceArea(con,before.getPracticeAreaId())+" to "+practiceArea(con,after.getPracticeAreaId()));
        }
    }

    private static void date(Connection c,long id,int t,int a,String event,String title,LocalDate oldValue,LocalDate newValue)throws SQLException{
        if(!Objects.equals(oldValue,newValue)) CaseTimelineWriter.append(c,id,t,a,event,title,"from "+value(oldValue)+" to "+value(newValue));
    }
    private static void bool(Connection c,long id,int t,int a,String event,String title,Boolean oldValue,Boolean newValue)throws SQLException{
        if(!Objects.equals(oldValue,newValue)) CaseTimelineWriter.append(c,id,t,a,event,title,Boolean.TRUE.equals(newValue)?"enabled":"disabled");
    }
    private static void text(Connection c,long id,int t,int a,String event,String title,String oldValue,String newValue,boolean redact)throws SQLException{
        String oldNormalized=normalize(oldValue),newNormalized=normalize(newValue);
        if(!Objects.equals(oldNormalized,newNormalized)) CaseTimelineWriter.append(c,id,t,a,event,title,redact?null:"from "+value(oldNormalized)+" to "+value(newNormalized));
    }
    private static String normalize(String value){if(value==null)return null;String v=value.trim();return v.isEmpty()?null:v;}
    private static String value(Object value){return value==null?"none":value.toString();}
    private static Boolean nullableBoolean(String value){String v=normalize(value);return v==null?null:Boolean.valueOf(v);}
    private static String practiceArea(Connection con,Integer id)throws SQLException{
        if(id==null)return "none";
        try(PreparedStatement ps=con.prepareStatement("SELECT Name FROM dbo.PracticeAreas WHERE Id=?")){ps.setInt(1,id);try(ResultSet rs=ps.executeQuery()){return rs.next()&&normalize(rs.getString(1))!=null?rs.getString(1).trim():"Practice area #"+id;}}
    }
}
