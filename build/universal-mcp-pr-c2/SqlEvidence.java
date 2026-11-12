import java.sql.*;import java.nio.file.*;import java.util.*;
public class SqlEvidence {
 static Connection open(String db)throws Exception {return DriverManager.getConnection("jdbc:sqlserver://localhost:15433;databaseName="+db+";encrypt=true;trustServerCertificate=false;loginTimeout=3;socketTimeout=10000","sa",Files.readString(Path.of("/workspace/Shale/work/pr-c2-sql-runtime/password")));}
 public static void main(String[] args)throws Exception {
  if(args.length==1&&args[0].equals("--cleanup")) {
   var counts=new LinkedHashMap<String,Object>();
   try(var c=open("master");var st=c.createStatement()) {
    try(var r=st.executeQuery("SELECT COUNT(*) FROM sys.databases WHERE name LIKE 'AiData[_]%' OR name LIKE 'AiOAuth[_]%'")){r.next();counts.put("remaining_fixture_databases",r.getInt(1));}
    try(var r=st.executeQuery("SELECT COUNT(*) FROM sys.sql_logins WHERE name LIKE 'AiData[_]%' OR name LIKE 'AiOAuth[_]%'")){r.next();counts.put("remaining_fixture_logins",r.getInt(1));}
   }
   System.out.println(new tools.jackson.databind.ObjectMapper().writeValueAsString(counts));return;
  }
  String control=args.length>0&&args[0].startsWith("--dcr-")?args[0]:null;
  String db=control==null&&args.length>0?args[0]:null;
  if(db==null)try(var c=open("master");var s=c.createStatement();var r=s.executeQuery("SELECT name FROM sys.databases WHERE name LIKE 'AiData[_]%'")) {
   while(r.next()){if(db!=null)throw new IllegalStateException("Exactly one disposable database required");db=r.getString(1);}
  }
  if(db==null||!db.matches("AiData_[0-9a-f]{32}_[0-9a-f]{32}"))throw new IllegalStateException("Nonce fixture required");
  var result=new LinkedHashMap<String,Object>();result.put("database","<disposable-nonce>");
  try(var c=open(db)) {
   if(control!=null) {
    if(!Set.of("--dcr-off","--dcr-on").contains(control))throw new IllegalArgumentException("Bounded disposable control only");
    try(var st=c.createStatement()){if(st.executeUpdate("UPDATE dbo.AiDcrControlV3 SET Enabled="+(control.equals("--dcr-on")?1:0)+" WHERE Id=1")!=1)throw new IllegalStateException("Control singleton required");}
    System.out.println("{\"disposable_dcr_control\":\""+control+"\"}");return;
   }
   var queries=new LinkedHashMap<String,String>();
   queries.put("dcr_applications","SELECT COUNT(*) FROM dbo.AiExternalApplication WHERE Source='DCR'");
   queries.put("dcr_creation_audits","SELECT COUNT(*) FROM dbo.GlobalControlPlaneAuditLog g JOIN dbo.AiExternalApplication e ON e.Id=g.EntityId WHERE e.Source='DCR' AND g.EntityType='AI_EXTERNAL_APPLICATION'");
   queries.put("cimd_applications","SELECT COUNT(*) FROM dbo.AiExternalApplication WHERE Source='CIMD'");
   queries.put("cimd_families","SELECT COUNT(*) FROM dbo.AiCimdFamilyV1");
   queries.put("returned_resources","SELECT COUNT(*) FROM dbo.IntegrationAccessAuditResource WHERE Relation='RETURNED'");
   queries.put("cimd_handoffs","SELECT COUNT(*) FROM dbo.AiCimdInvocationV1 WHERE HandoffConsumed=1 AND ReadEventId IS NOT NULL");
   queries.put("matched_disclosures","SELECT COUNT(DISTINCT i.InvocationId) FROM dbo.AiCimdInvocationV1 i JOIN dbo.IntegrationAccessAuditLog a ON a.ShaleClientId=i.ShaleClientId AND a.Id=i.ReadEventId AND a.InvocationId=i.InvocationId AND a.UserId=i.UserId AND a.Stage='READ' AND a.Outcome='ALLOWED_READ' JOIN dbo.IntegrationAccessAuditResource r ON r.ShaleClientId=a.ShaleClientId AND r.EventId=a.Id AND r.Relation='RETURNED' JOIN dbo.AiQuotaReservation q ON q.InvocationId=i.InvocationId AND q.ShaleClientId=i.ShaleClientId AND q.UserId=i.UserId AND q.ReleasedAt IS NOT NULL WHERE i.HandoffConsumed=1");
   queries.put("overview_handoffs","SELECT COUNT(*) FROM dbo.AiCimdInvocationV1 WHERE HandoffConsumed=1 AND Operation=2");
   queries.put("phi_overviews","SELECT COUNT(*) FROM dbo.auditLog WHERE FieldName='Case.Overview.Read'");
   queries.put("released_quota","SELECT COUNT(*) FROM dbo.AiQuotaReservation WHERE ReleasedAt IS NOT NULL");
   for(var q:queries.entrySet())try(var s=c.createStatement();var r=s.executeQuery(q.getValue())){r.next();result.put(q.getKey(),r.getInt(1));}
   for(String name:List.of("AiCaseDataDeployment","AiCimdProofReceiptV1","AiCimdOAuthReceiptV1"))try(var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM dbo."+name)){r.next();if(r.getInt(1)!=1)throw new IllegalStateException("Immutable singleton missing");}
  }
  System.out.println(new tools.jackson.databind.ObjectMapper().writeValueAsString(result));
 }
}
