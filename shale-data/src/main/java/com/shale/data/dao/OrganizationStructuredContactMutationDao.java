package com.shale.data.dao;

import static com.shale.core.service.OrganizationServicePort.*;

import java.sql.*;
import com.shale.data.validation.ContactValues;
import java.util.*;

/** Connection-bound Organization contact reconciler. It never opens or commits a connection. */
final class OrganizationStructuredContactMutationDao {
 private final EntityActionAuditDao audit=new EntityActionAuditDao();
 private record Row(long id,String kind,List<String> values,boolean primary,int order,boolean deleted,byte[] rowVer,boolean restorablePrimary){Row(long id,String kind,List<String> values,boolean primary,int order,boolean deleted,byte[] rowVer){this(id,kind,values,primary,order,deleted,rowVer,false);}}
 private record Spec(String table,String valueColumns,EntityActionAuditEvent.EntityType auditType){}
 private static final Spec PHONE=new Spec("OrganizationPhoneNumbers","DisplayNumber,NormalizedNumber,Extension",EntityActionAuditEvent.EntityType.ORGANIZATION_PHONE);
 private static final Spec EMAIL=new Spec("OrganizationEmailAddresses","EmailAddress,NormalizedEmail",EntityActionAuditEvent.EntityType.ORGANIZATION_EMAIL);
 private static final Spec ADDRESS=new Spec("OrganizationAddresses","AddressLine1,AddressLine2,City,StateOrProvince,PostalCode,Country,LegacyAddressText",EntityActionAuditEvent.EntityType.ORGANIZATION_ADDRESS);
 private static final Spec WEBSITE=new Spec("OrganizationWebsites","Website",EntityActionAuditEvent.EntityType.ORGANIZATION_WEBSITE);

 OrganizationFields reconcile(Connection c,int tenant,int organization,int actor,OrganizationFields fields,OrganizationContactMutation mutation)throws SQLException{
  Objects.requireNonNull(mutation,"contactMutation");
  if(mutation instanceof LegacyContactMutation legacy){
   if(legacy.email()!=null&&legacy.email().extension()!=null)throw new com.shale.core.validation.FieldValidationException("email","unsupported_extension","Email addresses do not have extensions.");
   if(legacy.phone()==null||!legacy.phone().retained())legacy(c,tenant,organization,actor,PHONE,"WORK",Arrays.asList(fields.phone(),null,legacy.phone()==null?null:nz(legacy.phone().extension())),legacyInput(legacy.phone(),fields.phone(),"phone"),false);
   if(legacy.fax()==null||!legacy.fax().retained())legacy(c,tenant,organization,actor,PHONE,"FAX",Arrays.asList(fields.fax(),null,legacy.fax()==null?null:nz(legacy.fax().extension())),legacyInput(legacy.fax(),fields.fax(),"fax"),true);
   if(legacy.email()==null||!legacy.email().retained())legacy(c,tenant,organization,actor,EMAIL,"WORK",Arrays.asList(fields.email(),null),legacy.email()==null?fields.email():legacy.email().input(),false);
   legacy(c,tenant,organization,actor,ADDRESS,"WORK",Arrays.asList(fields.address1(),fields.address2(),fields.city(),fields.state(),fields.postalCode(),fields.country(),null),first(fields.address1(),fields.address2(),fields.city(),fields.state(),fields.postalCode(),fields.country()),false);
   legacy(c,tenant,organization,actor,WEBSITE,"MAIN",Arrays.asList(fields.website()),fields.website(),false);return compatibility(c,tenant,organization,fields);
  }
  StructuredContactMutation x=(StructuredContactMutation)mutation;
  exact(c,tenant,organization,actor,PHONE,x.phones(),p->new Row(id(p.id()),kind(p.kind()),List.of(nz(p.displayNumber()),"",nz(p.extension())),p.primary(),p.sortOrder(),p.deleted(),p.expectedRowVer()));
  exact(c,tenant,organization,actor,EMAIL,x.emails(),e->new Row(id(e.id()),kind(e.kind()),List.of(nz(e.emailAddress()),""),e.primary(),e.sortOrder(),e.deleted(),e.expectedRowVer()));
  exact(c,tenant,organization,actor,ADDRESS,x.addresses(),a->new Row(id(a.id()),kind(a.kind()),List.of(nz(a.addressLine1()),nz(a.addressLine2()),nz(a.city()),nz(a.stateOrProvince()),nz(a.postalCode()),nz(a.country()),nz(a.legacyAddressText())),a.primary(),a.sortOrder(),a.deleted(),a.expectedRowVer()));
  exact(c,tenant,organization,actor,WEBSITE,x.websites(),w->new Row(id(w.id()),kind(w.kind()),List.of(nz(w.website())),w.primary(),w.sortOrder(),w.deleted(),w.expectedRowVer()));
  return compatibility(c,tenant,organization,fields);
 }
 OrganizationFields create(Connection c,int tenant,int organization,int actor,OrganizationFields fields,OrganizationContactMutation mutation)throws SQLException{
  if(mutation instanceof LegacyContactMutation){insertLegacy(c,tenant,organization,actor,fields);return compatibility(c,tenant,organization,fields);}
  return reconcile(c,tenant,organization,actor,fields,mutation);
 }
 private static String legacyInput(com.shale.core.validation.ValueUpdate update,String fallback,String field){return update==null?fallback:update.input();}

 private void insertLegacy(Connection c,int t,int o,int a,OrganizationFields f)throws SQLException{
  if(text(f.phone())!=null)insert(c,t,o,a,PHONE,new Row(0,"WORK",List.of(text(f.phone()),nz(normalizePhone(f.phone())),""),true,0,false,null));
  if(text(f.fax())!=null)insert(c,t,o,a,PHONE,new Row(0,"FAX",List.of(text(f.fax()),nz(normalizePhone(f.fax())),""),text(f.phone())==null, text(f.phone())==null?0:1,false,null));
  if(text(f.email())!=null)insert(c,t,o,a,EMAIL,new Row(0,"WORK",List.of(text(f.email()),nz(normalizeEmail(f.email()))),true,0,false,null));
  if(first(f.address1(),f.address2(),f.city(),f.state(),f.postalCode(),f.country())!=null)insert(c,t,o,a,ADDRESS,new Row(0,"WORK",List.of(nz(text(f.address1())),nz(text(f.address2())),nz(text(f.city())),nz(text(f.state())),nz(text(f.postalCode())),nz(text(f.country())),""),true,0,false,null));
  if(text(f.website())!=null)insert(c,t,o,a,WEBSITE,new Row(0,"MAIN",List.of(text(f.website())),true,0,false,null));
 }
 private interface Convert<T>{Row row(T value);}
 private <T> void exact(Connection c,int t,int o,int a,Spec s,OwnedContactCollection<T> owned,Convert<T> convert)throws SQLException{
  if(!owned.owned())return;List<Row> current=load(c,t,o,s),submitted=owned.rows().stream().map(convert::row).toList();Set<Long> ids=new HashSet<>();List<Row> active=submitted.stream().filter(r->!r.deleted).toList();
  for(int i=0;i<active.size();i++)if(active.get(i).order()!=i)throw new IllegalArgumentException("Active "+s.table+" ordering must be contiguous from zero.");
  long primaries=active.stream().filter(Row::primary).count();if(primaries>1)throw new IllegalArgumentException("Only one active primary is allowed for "+s.table+".");if(s!=PHONE&&!active.isEmpty()&&primaries!=1)throw new IllegalArgumentException("Exactly one active primary is required for "+s.table+".");
  if(s==PHONE){boolean voice=active.stream().anyMatch(r->!"FAX".equals(r.kind));if(voice&&(primaries!=1||active.stream().filter(Row::primary).anyMatch(r->"FAX".equals(r.kind))))throw new IllegalArgumentException("An active voice phone must be the single preferred phone.");}
  Map<Long,Row> byId=new HashMap<>();current.forEach(r->byId.put(r.id,r));
  for(Row r:submitted){if(r.id>0&&!ids.add(r.id))throw new IllegalArgumentException("Duplicate submitted structured contact ID.");if(r.id>0){Row old=byId.get(r.id);if(old==null)throw new IllegalArgumentException("Structured contact row does not belong to this Organization and tenant.");requireToken(r.rowVer);if(!Arrays.equals(old.rowVer,r.rowVer))stale();}else if(r.rowVer!=null)throw new IllegalArgumentException("A new structured contact row cannot include RowVer.");else if(r.deleted)throw new IllegalArgumentException("A new structured contact row cannot be deleted.");validate(s,r);validated(s,r,byId.get(r.id));}
  if(s==PHONE||s==EMAIL){Map<String,Boolean> keys=new HashMap<>();for(Row r:active){Row old=byId.get(r.id);boolean changed=valueChanged(s,r,old)||old!=null&&(!old.kind.equals(r.kind)||old.deleted);String key=duplicateKey(s,r);Boolean prior=keys.putIfAbsent(key,changed);if(prior!=null&&(prior||changed))throw new IllegalArgumentException("Duplicate active contact value and extension for this kind.");}}
  for(Row old:current)if(!old.deleted&&!ids.contains(old.id))remove(c,t,o,a,s,old);
  submitted.stream().sorted(Comparator.comparing(Row::primary)).forEach(r->{try{if(r.id>0)update(c,t,o,a,s,r,byId.get(r.id));else insert(c,t,o,a,s,r);}catch(SQLException ex){throw new SqlFailure(ex);}});
 }
 private void legacy(Connection c,int t,int o,int a,Spec s,String kind,List<String> values,String desired,boolean fax)throws SQLException{
  List<Row> active=load(c,t,o,s).stream().filter(r->!r.deleted&&(s!=PHONE||("FAX".equals(r.kind)==fax))).toList();Row owner=active.stream().filter(Row::primary).findFirst().orElse(active.isEmpty()?null:active.getFirst());String wanted=text(desired);if(owner!=null&&active.size()>1&&!same(owner.values.getFirst(),legacyScalar(c,t,o,s,fax)))throw new IllegalStateException("Legacy compatibility ownership is ambiguous; reload the structured profile before editing.");
  if(owner!=null&&Objects.equals(owner.values.getFirst(),desired)&&(s!=PHONE||values.get(2)==null||Objects.equals(nz(values.get(2)),nz(owner.values.get(2)))))return;
  if(s==PHONE&&wanted==null&&text(values.get(2))!=null)ContactValues.INSTANCE.phone(desired,values.get(2),false,"phone");
  if(wanted==null){if(owner!=null){remove(c,t,o,a,s,owner);compactAfterRemoval(c,t,o,a,s);}return;}if(s==PHONE){String extension=values.get(2);if(extension==null&&owner!=null&&ContactValues.INSTANCE.phone(desired,null,true,"FAX".equals(kind)?"fax":"phone").extension()==null)extension=owner.values.get(2);values=Arrays.asList(desired,null,extension);}else if(s==EMAIL)values=Arrays.asList(desired,null);
  Row next=new Row(owner==null?0:owner.id,owner==null?kind:owner.kind,values,owner==null?s!=PHONE||!fax:owner.primary,owner==null?nextOrder(load(c,t,o,s)):owner.order,false,owner==null?null:owner.rowVer);validate(s,next);rejectIntroducedDuplicate(s,next,owner,load(c,t,o,s));if(owner==null)insert(c,t,o,a,s,next);else update(c,t,o,a,s,next,owner);
 }
 private void compactAfterRemoval(Connection c,int t,int o,int a,Spec s)throws SQLException{
  List<Row> remaining=load(c,t,o,s).stream().filter(r->!r.deleted).toList();
  Row preferred=remaining.stream().filter(Row::primary).findFirst().orElse(null);
  if(preferred==null&&!remaining.isEmpty()){
   List<Row> candidates=s==PHONE&&remaining.stream().anyMatch(r->!"FAX".equals(r.kind))?remaining.stream().filter(r->!"FAX".equals(r.kind)).toList():remaining;
   preferred=candidates.stream().filter(r->s!=PHONE&&s!=EMAIL||s==PHONE&&ContactValues.INSTANCE.usablePhone(r.values.getFirst(),r.values.get(2))||s==EMAIL&&ContactValues.INSTANCE.usableEmail(r.values.getFirst())).findFirst().orElseThrow(()->new IllegalArgumentException("Correct a remaining contact value before selecting it as primary, or remove the remaining points together in the complete editor."));
  }
  for(int i=0;i<remaining.size();i++){Row old=remaining.get(i);update(c,t,o,a,s,new Row(old.id,old.kind,old.values,preferred!=null&&old.id==preferred.id,i,false,old.rowVer),old);}
 }
 private static boolean valueChanged(Spec s,Row r,Row old){return old==null||!Objects.equals(r.values.getFirst(),old.values.getFirst())||s==PHONE&&!Objects.equals(r.values.get(2),old.values.get(2));}
 private static String duplicateKey(Spec s,Row r){
  if(s==EMAIL)return r.kind+"|"+r.values.getFirst().toLowerCase(Locale.ROOT);
  String main=r.values.getFirst(),extension=r.values.get(2);
  if(ContactValues.INSTANCE.usablePhone(main,extension)){var parsed=ContactValues.INSTANCE.phone(main,extension,true,"phone");main=parsed.normalizedNumber();extension=nz(parsed.extension());}
  return r.kind+"|"+main+"|"+extension;
 }
 private static void rejectIntroducedDuplicate(Spec s,Row r,Row old,List<Row> current){if((s!=PHONE&&s!=EMAIL)||!valueChanged(s,r,old))return;String key=duplicateKey(s,validated(s,r,old));if(current.stream().anyMatch(other->!other.deleted&&other.id!=r.id&&duplicateKey(s,other).equals(key)))throw new IllegalArgumentException("Duplicate active contact value and extension for this kind.");}
 private static final class SqlFailure extends RuntimeException{SqlFailure(SQLException cause){super(cause);}}
 private static int nextOrder(List<Row> rows){return rows.stream().filter(r->!r.deleted).mapToInt(Row::order).max().orElse(-1)+1;}
 private static String legacyScalar(Connection c,int t,int o,Spec s,boolean fax)throws SQLException{String col=s==PHONE?(fax?"Fax":"Phone"):s==EMAIL?"Email":s==WEBSITE?"Website":"Address1";try(var p=c.prepareStatement("SELECT "+col+" FROM dbo.Organizations WHERE ShaleClientId=? AND Id=?")){p.setInt(1,t);p.setInt(2,o);try(var r=p.executeQuery()){return r.next()?r.getString(1):null;}}}
 private OrganizationFields compatibility(Connection c,int t,int o,OrganizationFields base)throws SQLException{List<Row> p=load(c,t,o,PHONE),e=load(c,t,o,EMAIL),ad=load(c,t,o,ADDRESS),w=load(c,t,o,WEBSITE);Row voice=preferred(p,r->!"FAX".equals(r.kind)),fax=first(p,r->"FAX".equals(r.kind)),email=preferred(e,r->true),address=preferred(ad,r->true),web=preferred(w,r->true);return new OrganizationFields(base.name(),value(voice,0),value(fax,0),value(email,0),value(web,0),value(address,0),value(address,1),value(address,2),value(address,3),value(address,4),value(address,5),base.notes());}
 private interface Accept{boolean yes(Row r);}private static Row preferred(List<Row> rows,Accept a){return rows.stream().filter(r->!r.deleted&&a.yes(r)&&r.primary).findFirst().orElse(first(rows,a));}private static Row first(List<Row> rows,Accept a){return rows.stream().filter(r->!r.deleted&&a.yes(r)).min(Comparator.comparingInt(Row::order).thenComparingLong(Row::id)).orElse(null);}private static String value(Row r,int i){return r==null||r.values.get(i).isEmpty()?null:r.values.get(i);}
 private List<Row> load(Connection c,int t,int o,Spec s)throws SQLException{String sql="SELECT Id,Kind,"+s.valueColumns+",IsPrimary,SortOrder,IsDeleted,RowVer FROM dbo."+s.table+" WITH(UPDLOCK,HOLDLOCK) WHERE ShaleClientId=? AND OrganizationId=? ORDER BY IsDeleted,SortOrder,Id";List<Row> out=new ArrayList<>();try(var p=c.prepareStatement(sql)){p.setInt(1,t);p.setInt(2,o);try(var r=p.executeQuery()){int n=s.valueColumns.split(",").length;while(r.next()){List<String> v=new ArrayList<>();for(int i=0;i<n;i++)v.add(nz(r.getString(3+i)));out.add(new Row(r.getLong(1),r.getString(2),List.copyOf(v),r.getBoolean(3+n),r.getInt(4+n),r.getBoolean(5+n),r.getBytes(6+n),false));}}}if(s==PHONE||s==EMAIL)for(int i=0;i<out.size();i++){Row old=out.get(i);if(old.deleted)out.set(i,new Row(old.id,old.kind,old.values,old.primary,old.order,old.deleted,old.rowVer,ContactPointHistory.wasPrimary(c,t,s.auditType.name(),old.id,"ORGANIZATION",o)));}return out;}
 private void insert(Connection c,int t,int o,int a,Spec s,Row r)throws SQLException{r=validated(s,r,null);String q=String.join(",",Collections.nCopies(r.values.size(),"?"));String sql="INSERT dbo."+s.table+"(ShaleClientId,OrganizationId,Kind,"+s.valueColumns+",IsPrimary,SortOrder,IsDeleted,CreatedAt,CreatedByUserId) OUTPUT INSERTED.Id VALUES(?,?,?,"+q+",?,?,0,SYSUTCDATETIME(),?)";long id;try(var p=c.prepareStatement(sql)){int i=1;p.setInt(i++,t);p.setInt(i++,o);p.setString(i++,r.kind);for(String v:r.values)set(p,i++,v);p.setBoolean(i++,r.primary);p.setInt(i++,r.order);p.setInt(i,a);try(var x=p.executeQuery()){if(!x.next())throw new IllegalStateException("Structured contact row was not created.");id=x.getLong(1);}}audit(c,t,o,a,s,id,EntityActionAuditEvent.Action.CREATED,r);}
 private void update(Connection c,int t,int o,int a,Spec s,Row r,Row old)throws SQLException{r=validated(s,r,old);if(equal(r,old))return;String sets=Arrays.stream(s.valueColumns.split(",")).map(x->x+"=?").reduce((x,y)->x+","+y).orElseThrow();String sql="UPDATE dbo."+s.table+" SET Kind=?,"+sets+",IsPrimary=?,SortOrder=?,IsDeleted=?,DeletedAt="+(r.deleted?"SYSUTCDATETIME()":"NULL")+",DeletedByUserId="+(r.deleted?"?":"NULL")+",UpdatedAt=SYSUTCDATETIME(),UpdatedByUserId=? WHERE Id=? AND ShaleClientId=? AND OrganizationId=? AND RowVer=?";try(var p=c.prepareStatement(sql)){int i=1;p.setString(i++,r.kind);for(String v:r.values)set(p,i++,v);p.setBoolean(i++,r.deleted?false:r.primary);p.setInt(i++,r.order);p.setBoolean(i++,r.deleted);if(r.deleted)p.setInt(i++,a);p.setInt(i++,a);p.setLong(i++,r.id);p.setInt(i++,t);p.setInt(i++,o);p.setBytes(i,r.rowVer);if(p.executeUpdate()!=1)stale();}audit(c,t,o,a,s,r.id,r.deleted&&!old.deleted?EntityActionAuditEvent.Action.REMOVED:!r.deleted&&old.deleted?EntityActionAuditEvent.Action.RESTORED:EntityActionAuditEvent.Action.UPDATED,r);}
 private void remove(Connection c,int t,int o,int a,Spec s,Row old)throws SQLException{Row r=new Row(old.id,old.kind,old.values,false,old.order,true,old.rowVer);update(c,t,o,a,s,r,old);}
 private void audit(Connection c,int t,int o,int a,Spec s,long id,EntityActionAuditEvent.Action action,Row r)throws SQLException{audit.append(c,EntityActionAuditEvent.now(t,a,s.auditType,id,action,EntityActionAuditEvent.EntityType.ORGANIZATION,Long.valueOf(o),Map.of(EntityActionAuditEvent.MetadataKey.ORGANIZATION_ID,o,EntityActionAuditEvent.MetadataKey.KIND,r.kind,EntityActionAuditEvent.MetadataKey.PRIMARY,r.primary)));}
 private static void validate(Spec s,Row r){if(r.order<0)throw new IllegalArgumentException("Structured contact order cannot be negative.");if(r.kind==null||r.kind.equals("UNKNOWN"))throw new IllegalArgumentException("A supported structured contact kind is required.");if(s==ADDRESS){if(r.values.stream().allMatch(v->text(v)==null))throw new IllegalArgumentException("An address must contain at least one meaningful component.");}else if(text(r.values.getFirst())==null)throw new IllegalArgumentException("Structured contact values cannot be blank.");if(s==PHONE&&text(r.values.get(2))!=null&&r.values.get(2).length()>20)throw new IllegalArgumentException("Phone extension cannot exceed 20 characters.");}
 private static Row validated(Spec s,Row r,Row old){
  if(s!=PHONE&&s!=EMAIL)return r;
  boolean changed=old==null||!Objects.equals(r.values.getFirst(),old.values.getFirst())||s==PHONE&&!Objects.equals(r.values.get(2),old.values.get(2));
  boolean newPrimary=r.primary&&(old==null||!old.primary)&&!(old!=null&&old.deleted&&old.restorablePrimary);
  List<String> values=old==null?r.values:old.values;
  if(changed||newPrimary){
   if(s==PHONE){var v=ContactValues.INSTANCE.phone(r.values.getFirst(),r.values.get(2),true,"FAX".equals(r.kind)?"fax":"phone");if(changed)values=List.of(v.displayInput(),v.normalizedNumber(),nz(v.extension()));}
   else {var v=ContactValues.INSTANCE.email(r.values.getFirst(),true,"email");if(changed)values=List.of(v.displayInput(),v.comparisonKey());}
  }
  return new Row(r.id,r.kind,values,r.primary,r.order,r.deleted,r.rowVer);
 }
 private static boolean equal(Row a,Row b){return a.kind.equals(b.kind)&&a.values.equals(b.values)&&a.primary==b.primary&&a.order==b.order&&a.deleted==b.deleted;}
 private static long id(Long x){return x==null?0:x;}private static String kind(Enum<?> x){if(x==null||"UNKNOWN".equals(x.name()))throw new IllegalArgumentException("A supported structured contact kind is required.");return x.name();}private static void requireToken(byte[] b){if(b==null||b.length==0)throw new IllegalArgumentException("Opening RowVer is required for every existing structured row.");}private static void stale(){throw new IllegalStateException("Organization was changed by another user. Reload and try again.");}
 private static String normalizePhone(String x){if(text(x)==null)return null;String d=x.replaceAll("[^0-9+]","");return d.matches("\\+?[0-9]{7,15}")?d:null;}private static String normalizeEmail(String x){String v=text(x);return v!=null&&v.indexOf('@')>0&&!v.contains(" ")?v.toLowerCase(Locale.ROOT):null;}private static String first(String...x){return Arrays.stream(x).map(OrganizationStructuredContactMutationDao::text).filter(Objects::nonNull).findFirst().orElse(null);}private static String text(String x){if(x==null)return null;String v=x.trim();return v.isEmpty()?null:v;}private static String nz(String x){return x==null?"":x;}private static boolean same(String a,String b){return Objects.equals(text(a),text(b));}private static void set(PreparedStatement p,int i,String v)throws SQLException{if(v==null||v.isEmpty())p.setNull(i,Types.NVARCHAR);else p.setString(i,v);}
}
