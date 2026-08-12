package com.shale.data.dao;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
class WebCaseCreationCaseDatesContractTest {
 @Test void aggregateIsConnectionBoundAuditedAndNeverWritesLegacyCaseDates()throws Exception{
  String c=Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseDao.java")),m=method(c,"long insertBasicCaseAggregate");
  for(String x:new String[]{"CallerDate","CallerTime","DateOfInjury","DateOfMedicalNegligence","DateMedicalNegligenceWasDiscovered","StatuteOfLimitations","TortNoticeDeadline","DiscoveryDeadline","DateFeeAgreementSigned","DateNonEngagementLetterSent"})assertFalse(m.contains(x),x);
  String d=Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseDateDao.java")),a=method(d,"public long createCaseAggregate");
  assertTrue(a.contains("insertBasicCaseAggregate(con"));assertTrue(a.contains("insertCreatedMappedDate(con"));assertTrue(a.contains("entityActionAuditDao.append(con"));assertTrue(a.contains("phiAuditService.auditCreate(con"));assertFalse(a.contains("con.commit"));
  assertTrue(d.contains("new CaseAggregateTransaction(db).execute"));assertTrue(d.contains("SESSION_CONTEXT(N'PrincipalUserId')"));assertTrue(d.contains("SYSUTCDATETIME()"));
 }
 private static String method(String s,String sig){int st=s.indexOf(sig);assertTrue(st>=0);int b=s.indexOf('{',st),n=0;for(int i=b;i<s.length();i++){char c=s.charAt(i);if(c=='{')n++;else if(c=='}'&&--n==0)return s.substring(st,i+1);}throw new AssertionError();}
}
