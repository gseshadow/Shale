package com.shale.data.security.ai;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

/** Test-only metadata comparison. No catalog text crosses JDBC or enters evidence. */
final class AzureCatalogComponents {
    static final List<String> NAMES=List.of("objects_and_modules","columns","constraints","foreign_key_columns",
            "indexes","security_predicates","object_permissions","triggers","integration_role_owners",
            "tenant_filter_definition","runtime_verifier_permissions","execution_and_schema_owners","dependencies",
            "runtime_role_owner","verifier_role_owner","database_options","unicode_ranges","unicode_lower","word_transitions");
    // Four preamble statements plus one SET per SELECT in the fixed resource.
    // JDBC normally treats false/-1 as terminal. Tolerate exposed non-result states
    // only within this finite budget per gap; never execute again or retry SQL.
    static final int NON_RESULT_BUDGET=4+NAMES.size();
    record Component(long bytes,String sha256) {
        Component {
            if(bytes<0||sha256==null||!sha256.matches("[0-9A-Fa-f]{64}"))throw refused();
            sha256=sha256.toLowerCase(Locale.ROOT);
        }
    }
    static String sql()throws IOException {
        try(var input=AzureCatalogComponents.class.getResourceAsStream("/ai/confinement-v2-catalog-components.sql")) {
            if(input==null)throw refused();
            return new String(input.readAllBytes(),StandardCharsets.UTF_8);
        }
    }
    static List<Component> read(Connection connection)throws SQLException,IOException {
        int component=0; // execute/preamble failure cannot identify the first result.
        try {
            var rows=new ArrayList<Component>();
            try(var statement=connection.createStatement()) {
                statement.setQueryTimeout(AiCaseDataAzureSqlDisposableSetupTest.OWNER_QUERY_SECONDS);
                boolean more=statement.execute(sql());
                int nonResults=0;
                while(true) {
                    if(more) {
                        component=Math.min(rows.size()+1,NAMES.size());
                        try(var result=statement.getResultSet()) {
                            int index=rows.size();
                            if(index>=NAMES.size()||!result.next())throw refused();
                            if(result.getInt(1)!=index+1||!NAMES.get(index).equals(result.getString(2)))throw refused();
                            long bytes=result.getLong(3);
                            if(result.wasNull())throw refused();
                            rows.add(new Component(bytes,result.getString(4)));
                            if(result.next())throw refused();
                            nonResults=0;
                        }
                    }
                    else {
                        if(statement.getUpdateCount()!=-1)throw refused(); // No update counts, including zero.
                        if(++nonResults>NON_RESULT_BUDGET)break;
                    }
                    // Advancement identifies only the next expected fixed slot, never driver text.
                    if(!rows.isEmpty())component=Math.min(rows.size()+1,NAMES.size());
                    more=statement.getMoreResults(Statement.CLOSE_CURRENT_RESULT);
                }
            }
            if(rows.size()!=NAMES.size())throw refused();
            return List.copyOf(rows);
        } catch(Exception|AssertionError failure) {
            // Outside JDBC resource scopes: discard causes and suppressed close failures too.
            throw new ReadFailure(component);
        }
    }
    static final class ReadFailure extends IllegalStateException {
        private final int component;
        private ReadFailure(int component){super("Incomplete or invalid catalog component metadata; discard fixture; no retry/adoption");this.component=component;}
        String componentLabel(){return component==0?"0:before_first_component":component+":"+NAMES.get(component-1);}
    }
    static List<String> report(List<Component> owner,List<Component> verifier) {
        if(owner.size()!=NAMES.size()||verifier.size()!=NAMES.size())throw refused();
        var lines=new ArrayList<String>();
        lines.add("ordinal\tname\townerBytes\tverifierBytes\townerSha256\tverifierSha256\tequal");
        for(int i=0;i<NAMES.size();i++) {
            var a=owner.get(i);var b=verifier.get(i);
            lines.add((i+1)+"\t"+NAMES.get(i)+"\t"+a.bytes()+"\t"+b.bytes()+"\t"+a.sha256()+"\t"+b.sha256()+"\t"+a.equals(b));
        }
        return List.copyOf(lines);
    }
    static List<String> differences(List<Component> owner,List<Component> verifier) {
        if(owner.size()!=NAMES.size()||verifier.size()!=NAMES.size())throw refused();
        var names=new ArrayList<String>();
        for(int i=0;i<NAMES.size();i++)if(!owner.get(i).equals(verifier.get(i)))names.add((i+1)+":"+NAMES.get(i));
        return List.copyOf(names);
    }
    static IllegalStateException refused(){return new IllegalStateException("Incomplete or invalid catalog component metadata; discard fixture; no retry/adoption");}
    private AzureCatalogComponents(){}
}
