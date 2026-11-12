import java.sql.*;import java.nio.file.*;import java.util.*;
public class SqlEvidence {
 static Connection open(String db)throws Exception {return DriverManager.getConnection("jdbc:sqlserver://localhost:15433;databaseName="+db+";encrypt=true;trustServerCertificate=false;loginTimeout=3;socketTimeout=10000","sa",Files.readString(Path.of("/workspace/Shale/work/pr-b-sql/sa-password")));}
 public static void main(String[] args)throws Exception {
  String db=args.length>0?args[0]:null;
  if(db==null)try(var c=open("master");var s=c.createStatement();var r=s.executeQuery("SELECT name FROM sys.databases WHERE name LIKE 'AiData[_]%'")) {
   while(r.next()){if(db!=null)throw new IllegalStateException("Exactly one disposable database required");db=r.getString(1);}
  }
  if(db==null||!db.matches("AiData_[0-9a-f]{32}_[0-9a-f]{32}"))throw new IllegalStateException("Nonce fixture required");
  var result=new LinkedHashMap<String,Object>();result.put("database",db);
  try(var c=open(db)) {
   var queries=new LinkedHashMap<String,String>();
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
