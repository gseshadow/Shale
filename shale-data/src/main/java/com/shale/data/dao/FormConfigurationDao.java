package com.shale.data.dao;

import com.shale.core.dto.FormConfigurationDto;
import com.shale.core.runtime.DbSessionProvider;
import com.shale.core.service.FormConfigurationServicePort.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

/** Transactional persistence boundary for generic tenant form configuration. */
public final class FormConfigurationDao {
    private static final Set<String> FORMS = Set.of("NEW_INTAKE");
    private static final Set<String> KINDS = Set.of("CASE_DATE");
    private static final Pattern KEY = Pattern.compile("[a-z][a-z0-9_.:-]{0,127}");
    private final DbSessionProvider db;

    public FormConfigurationDao(DbSessionProvider db) { this.db = Objects.requireNonNull(db, "db"); }

    public FormConfigurationDto load(int tenant, int actor, String formKey) {
        formKey = validateFormKey(formKey);
        try (Connection con = db.requireConnection()) {
            verifyContext(con, tenant); validateActor(con, tenant, actor);
            return load(con, tenant, formKey);
        } catch (SQLException e) { throw failure(e); }
    }

    public FormConfigurationDto replace(ReplaceCommand command) {
        Objects.requireNonNull(command, "command");
        String formKey = validate(command);
        try (Connection con = db.requireConnection()) {
            verifyContext(con, command.shaleClientId());
            con.setAutoCommit(false);
            try {
                validateActor(con, command.shaleClientId(), command.actorUserId());
                validateReferences(con, command.shaleClientId(), command.sections());
                long formId = lockAndTouchForm(con, command, formKey);
                try (PreparedStatement ps = con.prepareStatement("DELETE FROM dbo.FormConfiguredFields WHERE FormConfigurationId=? AND ShaleClientId=?")) {
                    ps.setLong(1, formId); ps.setInt(2, command.shaleClientId()); ps.executeUpdate();
                }
                try (PreparedStatement ps = con.prepareStatement("DELETE FROM dbo.FormConfigurationSections WHERE FormConfigurationId=? AND ShaleClientId=?")) {
                    ps.setLong(1, formId); ps.setInt(2, command.shaleClientId()); ps.executeUpdate();
                }
                insertSections(con, formId, command);
                con.commit();
                return load(con, command.shaleClientId(), formKey);
            } catch (Exception e) {
                try { con.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); }
                if (e instanceof RuntimeException runtime) throw runtime;
                throw e;
            }
        } catch (SQLException e) { throw failure(e); }
    }

    private static String validate(ReplaceCommand c) {
        if (c.shaleClientId() <= 0 || c.actorUserId() <= 0) throw new IllegalArgumentException("Tenant and actor are required.");
        String formKey = validateFormKey(c.formKey());
        Set<String> sections = new HashSet<>(); Set<String> formFields = new HashSet<>(); Set<Integer> sectionOrders = new HashSet<>(); Set<Integer> dateTypes = new HashSet<>();
        for (SectionDraft section : c.sections()) {
            if (section == null || !validKey(section.sectionKey())) throw new IllegalArgumentException("A stable sectionKey is required.");
            if (!sections.add(section.sectionKey())) throw new IllegalArgumentException("Duplicate section key.");
            if (section.title() == null || section.title().isBlank() || section.title().length() > 200) throw new IllegalArgumentException("Section title is required and limited to 200 characters.");
            if (section.sortOrder() < 0 || !sectionOrders.add(section.sortOrder())) throw new IllegalArgumentException("Section ordering must be non-negative and unique.");
            Set<Integer> fieldOrders = new HashSet<>();
            for (FieldDraft field : section.fields()) {
                if (field == null || !validKey(field.fieldKey())) throw new IllegalArgumentException("A stable fieldKey is required.");
                if (!formFields.add(field.fieldKey())) throw new IllegalArgumentException("Duplicate field key in form.");
                if (field.sortOrder() < 0 || !fieldOrders.add(field.sortOrder())) throw new IllegalArgumentException("Field ordering must be non-negative and unique within its section.");
                if (!KINDS.contains(field.fieldKind())) throw new IllegalArgumentException("Unsupported field kind.");
                if ("CASE_DATE".equals(field.fieldKind())) {
                    if (field.caseDateTypeId() == null || field.caseDateTypeId() <= 0) throw new IllegalArgumentException("CASE_DATE requires CaseDateTypeId.");
                    if (!dateTypes.add(field.caseDateTypeId())) throw new IllegalArgumentException("A case-date type may appear only once in a form.");
                }
            }
        }
        return formKey;
    }

    private static String validateFormKey(String key) {
        if (key == null || !FORMS.contains(key)) throw new IllegalArgumentException("Unsupported form key.");
        return key;
    }
    private static boolean validKey(String key) { return key != null && KEY.matcher(key).matches(); }

    private static void validateReferences(Connection con, int tenant, List<SectionDraft> sections) throws SQLException {
        String sql = "SELECT ShaleClientId,IsActive,IsDeleted FROM dbo.CaseDateTypes WHERE Id=? AND (ShaleClientId=? OR ShaleClientId IS NULL)";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            for (SectionDraft section : sections) for (FieldDraft field : section.fields()) if ("CASE_DATE".equals(field.fieldKind())) {
                ps.setInt(1, field.caseDateTypeId()); ps.setInt(2, tenant);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new IllegalArgumentException("Case-date type is missing or belongs to another tenant.");
                    if (!rs.getBoolean("IsActive") || rs.getBoolean("IsDeleted")) throw new IllegalArgumentException("Case-date type is inactive or deleted.");
                }
            }
        }
    }

    private static long lockAndTouchForm(Connection con, ReplaceCommand c, String key) throws SQLException {
        Long id = null; byte[] current = null;
        try (PreparedStatement ps = con.prepareStatement("SELECT Id,RowVer FROM dbo.FormConfigurations WITH (UPDLOCK,HOLDLOCK) WHERE ShaleClientId=? AND FormKey=? AND IsDeleted=0")) {
            ps.setInt(1,c.shaleClientId()); ps.setString(2,key); try(ResultSet rs=ps.executeQuery()){if(rs.next()){id=rs.getLong(1);current=rs.getBytes(2);}}
        }
        if (id == null) {
            if (c.expectedRowVer() != null && c.expectedRowVer().length > 0) throw new IllegalStateException("Form configuration changed.");
            try (PreparedStatement ps=con.prepareStatement("INSERT dbo.FormConfigurations(ShaleClientId,FormKey,CreatedByUserId,UpdatedByUserId) VALUES(?,?,?,?)",Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1,c.shaleClientId());ps.setString(2,key);ps.setInt(3,c.actorUserId());ps.setInt(4,c.actorUserId());ps.executeUpdate();
                try(ResultSet rs=ps.getGeneratedKeys()){if(!rs.next())throw new SQLException("No generated form configuration id.");return rs.getLong(1);}
            }
        }
        if (c.expectedRowVer()==null || !Arrays.equals(current,c.expectedRowVer())) throw new IllegalStateException("Form configuration changed.");
        try(PreparedStatement ps=con.prepareStatement("UPDATE dbo.FormConfigurations SET UpdatedAt=SYSUTCDATETIME(),UpdatedByUserId=? WHERE Id=? AND ShaleClientId=? AND RowVer=?")){
            ps.setInt(1,c.actorUserId());ps.setLong(2,id);ps.setInt(3,c.shaleClientId());ps.setBytes(4,c.expectedRowVer());if(ps.executeUpdate()!=1)throw new IllegalStateException("Form configuration changed.");
        }
        return id;
    }

    private static void insertSections(Connection con,long formId,ReplaceCommand c)throws SQLException{
        String ss="INSERT dbo.FormConfigurationSections(ShaleClientId,FormConfigurationId,SectionKey,Title,SortOrder,IsEnabled,IsVisible,CreatedByUserId,UpdatedByUserId) VALUES(?,?,?,?,?,?,?,?,?)";
        String fs="INSERT dbo.FormConfiguredFields(ShaleClientId,FormConfigurationId,FormConfigurationSectionId,FieldKey,FieldKind,CaseDateTypeId,SortOrder,IsEnabled,IsVisible,IsRequired,CreatedByUserId,UpdatedByUserId) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)";
        try(PreparedStatement section=con.prepareStatement(ss,Statement.RETURN_GENERATED_KEYS);PreparedStatement field=con.prepareStatement(fs)){
            for(SectionDraft s:c.sections()){
                int i=1;section.setInt(i++,c.shaleClientId());section.setLong(i++,formId);section.setString(i++,s.sectionKey());section.setString(i++,s.title().trim());section.setInt(i++,s.sortOrder());section.setBoolean(i++,s.enabled());section.setBoolean(i++,s.visible());section.setInt(i++,c.actorUserId());section.setInt(i,c.actorUserId());section.executeUpdate();
                long sectionId;try(ResultSet rs=section.getGeneratedKeys()){if(!rs.next())throw new SQLException("No generated section id.");sectionId=rs.getLong(1);}
                for(FieldDraft f:s.fields()){i=1;field.setInt(i++,c.shaleClientId());field.setLong(i++,formId);field.setLong(i++,sectionId);field.setString(i++,f.fieldKey());field.setString(i++,f.fieldKind());if(f.caseDateTypeId()==null)field.setNull(i++,Types.INTEGER);else field.setInt(i++,f.caseDateTypeId());field.setInt(i++,f.sortOrder());field.setBoolean(i++,f.enabled());field.setBoolean(i++,f.visible());field.setBoolean(i++,f.required());field.setInt(i++,c.actorUserId());field.setInt(i,c.actorUserId());field.executeUpdate();}
            }
        }
    }

    private static FormConfigurationDto load(Connection con,int tenant,String key)throws SQLException{
        long id;LocalDateTime created,updated;byte[] rv;
        try(PreparedStatement ps=con.prepareStatement("SELECT Id,CreatedAt,UpdatedAt,RowVer FROM dbo.FormConfigurations WHERE ShaleClientId=? AND FormKey=? AND IsDeleted=0")){ps.setInt(1,tenant);ps.setString(2,key);try(ResultSet rs=ps.executeQuery()){if(!rs.next())return new FormConfigurationDto(0,tenant,key,List.of(),null,null,null);id=rs.getLong(1);created=ldt(rs,"CreatedAt");updated=ldt(rs,"UpdatedAt");rv=rs.getBytes("RowVer");}}
        LinkedHashMap<Long,SectionBuilder> sections=new LinkedHashMap<>();
        String sql="SELECT s.Id SectionId,s.SectionKey,s.Title,s.SortOrder SectionSort,s.IsEnabled SectionEnabled,s.IsVisible SectionVisible,f.Id FieldId,f.FieldKey,f.FieldKind,f.CaseDateTypeId,f.SortOrder FieldSort,f.IsEnabled FieldEnabled,f.IsVisible FieldVisible,f.IsRequired FROM dbo.FormConfigurationSections s LEFT JOIN dbo.FormConfiguredFields f ON f.FormConfigurationSectionId=s.Id AND f.ShaleClientId=s.ShaleClientId WHERE s.FormConfigurationId=? AND s.ShaleClientId=? ORDER BY s.SortOrder,s.Id,f.SortOrder,f.Id";
        try(PreparedStatement ps=con.prepareStatement(sql)){ps.setLong(1,id);ps.setInt(2,tenant);try(ResultSet rs=ps.executeQuery()){while(rs.next()){long sid=rs.getLong("SectionId");SectionBuilder b=sections.computeIfAbsent(sid,x->new SectionBuilder(sid,get(rs,"SectionKey"),get(rs,"Title"),integer(rs,"SectionSort"),bool(rs,"SectionEnabled"),bool(rs,"SectionVisible")));Long fid=(Long)object(rs,"FieldId");if(fid!=null)b.fields.add(new FormConfigurationDto.Field(fid,get(rs,"FieldKey"),get(rs,"FieldKind"),(Integer)object(rs,"CaseDateTypeId"),integer(rs,"FieldSort"),bool(rs,"FieldEnabled"),bool(rs,"FieldVisible"),bool(rs,"IsRequired")));}}}
        List<FormConfigurationDto.Section> out=sections.values().stream().map(SectionBuilder::dto).toList();return new FormConfigurationDto(id,tenant,key,out,created,updated,rv);
    }
    private static final class SectionBuilder{final long id;final String key,title;final int order;final boolean enabled,visible;final List<FormConfigurationDto.Field> fields=new ArrayList<>();SectionBuilder(long i,String k,String t,int o,boolean e,boolean v){id=i;key=k;title=t;order=o;enabled=e;visible=v;}FormConfigurationDto.Section dto(){return new FormConfigurationDto.Section(id,key,title,order,enabled,visible,fields);}}
    private static Object object(ResultSet r,String n){try{return r.getObject(n);}catch(SQLException e){throw failure(e);}}private static String get(ResultSet r,String n){try{return r.getString(n);}catch(SQLException e){throw failure(e);}}private static int integer(ResultSet r,String n){try{return r.getInt(n);}catch(SQLException e){throw failure(e);}}private static boolean bool(ResultSet r,String n){try{return r.getBoolean(n);}catch(SQLException e){throw failure(e);}}private static LocalDateTime ldt(ResultSet r,String n)throws SQLException{Timestamp t=r.getTimestamp(n);return t==null?null:t.toLocalDateTime();}
    private static void verifyContext(Connection con,int tenant)throws SQLException{try(PreparedStatement ps=con.prepareStatement("SELECT TRY_CONVERT(int,SESSION_CONTEXT(N'ShaleClientId'))");ResultSet rs=ps.executeQuery()){if(!rs.next()||rs.getObject(1)==null||rs.getInt(1)!=tenant)throw new IllegalStateException("Tenant session context is not initialized.");}}
    private static void validateActor(Connection con,int tenant,int actor)throws SQLException{try(PreparedStatement ps=con.prepareStatement("SELECT 1 FROM dbo.Users WHERE Id=? AND ShaleClientId=? AND ISNULL(is_deleted,0)=0")){ps.setInt(1,actor);ps.setInt(2,tenant);try(ResultSet rs=ps.executeQuery()){if(!rs.next())throw new IllegalArgumentException("Actor is not available for this tenant.");}}}
    private static RuntimeException failure(SQLException e){return new IllegalStateException("Form configuration persistence failed.",e);}
}
