package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class OrganizationStructuredContactReadContractTest {
 @Test void boundedReadUsesExplicitTenantOwnershipOrderingAndNoMutationSql()throws Exception{
  String source=Files.readString(Path.of("src/main/java/com/shale/data/dao/OrganizationDao.java")).replace("\r\n","\n");
  String method=body(source,"public StructuredContactProfileRow findStructuredContactProfile(");
  assertTrue(method.contains("verifyTenantMatchesSession(con,shaleClientId)"));
  assertTrue(method.contains("WHERE ShaleClientId=? AND Id=?"));
  for(String table:new String[]{"OrganizationPhoneNumbers","OrganizationEmailAddresses","OrganizationAddresses","OrganizationWebsites"})assertTrue(source.contains("FROM dbo."+table+" WHERE ShaleClientId=? AND OrganizationId=?"),table+" must have explicit owner predicates");
  assertEquals(4,count(source,"+CONTACT_ORDER"),"exactly one bounded query is used per structured concept");
  assertEquals(" ORDER BY IsDeleted,SortOrder,Id",constant(source,"CONTACT_ORDER"));
  assertFalse(method.matches("(?is).*\\b(?:INSERT|UPDATE|DELETE|MERGE)\\s+(?:INTO\\s+)?dbo\\..*"));
  assertFalse(source.contains("SELECT * FROM dbo.Organization"));
 }
 private static int count(String s,String token){int n=0,p=0;while((p=s.indexOf(token,p))>=0){n++;p+=token.length();}return n;}
 private static String constant(String s,String name){int p=s.indexOf(name+"=\"")+name.length()+2;return s.substring(p,s.indexOf('"',p));}
 private static String body(String source,String signature){int start=source.indexOf(signature);assertTrue(start>=0);int opening=source.indexOf('{',start),depth=0;for(int i=opening;i<source.length();i++){if(source.charAt(i)=='{')depth++;if(source.charAt(i)=='}'&&--depth==0)return source.substring(opening,i+1);}throw new AssertionError("unbalanced");}
}
