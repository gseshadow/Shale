package com.shale.server.config;

import java.math.BigDecimal;
import java.util.List;
import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Only the additive read schemas are refined; legacy schemas and consumers remain untouched. */
@Configuration
public class MinimizedCaseOpenApiConfiguration {
    @Bean
    OpenApiCustomizer minimizedCaseSchemas() {
        return api -> {
            var schemas = api.getComponents().getSchemas();
            Schema<?> overview = schemas.get("MinimizedCaseOverview");
            if (overview == null) return;
            overview.setRequired(List.of("caseId", "caseNumber", "caseName", "status", "practiceArea", "responsibleAttorney", "primaryLegalAssistant", "updatedAt"));
            overview.setAdditionalProperties(false);
            Schema<?> id = overview.getProperties().get("caseId");
            id.setMinimum(BigDecimal.ONE); id.setMaximum(BigDecimal.valueOf(Integer.MAX_VALUE));
            text(overview,"caseName",255,false); text(overview,"caseNumber",200,true);
            for (String key : List.of("status","practiceArea","responsibleAttorney","primaryLegalAssistant")) {
                Schema<?> reference = overview.getProperties().get(key);
                Schema<Object> nullable = new Schema<>();
                nullable.setType("object"); nullable.addAllOfItem(reference); nullable.setNullable(true);
                overview.getProperties().put(key, nullable);
            }
            overview.getProperties().get("updatedAt").setNullable(true);
            Schema<?> timestamp = overview.getProperties().get("updatedAt");
            timestamp.setDescription("Nullable stored local date-time text; no timezone or Z conversion.");
            timestamp.setFormat(null);
            refine(schemas.get("CaseReadStatus"),List.of("id","name","color"));
            text(schemas.get("CaseReadStatus"),"name",255,false);text(schemas.get("CaseReadStatus"),"color",7,true);
            ((Schema<?>) schemas.get("CaseReadStatus").getProperties().get("color")).setPattern("^#[0-9a-fA-F]{6}$");
            refine(schemas.get("CaseReadPracticeArea"),List.of("id","name"));text(schemas.get("CaseReadPracticeArea"),"name",255,false);
            refine(schemas.get("CaseReadUser"),List.of("userId","displayName"));text(schemas.get("CaseReadUser"),"displayName",255,false);
            for (String record : List.of("CaseReadStatus", "CaseReadPracticeArea", "CaseReadUser")) {
                Schema<?> relationship = schemas.get(record);
                String identity = record.equals("CaseReadUser") ? "userId" : "id";
                relationship.getProperties().get(identity).setMinimum(BigDecimal.ONE);
                relationship.getProperties().get(identity).setMaximum(BigDecimal.valueOf(Integer.MAX_VALUE));
            }
            Schema<?> page=schemas.get("MinimizedCasePage");
            refine(page,List.of("items","page","size","hasMore"));
            page.getProperties().get("items").setMaxItems(25);
            page.getProperties().get("page").setMinimum(BigDecimal.ZERO);page.getProperties().get("page").setMaximum(BigDecimal.valueOf(100));
            page.getProperties().get("size").setMinimum(BigDecimal.ONE);page.getProperties().get("size").setMaximum(BigDecimal.valueOf(25));
        };
    }
    private static void refine(Schema<?> schema,List<String> required) { schema.setRequired(required);schema.setAdditionalProperties(false); }
    private static void text(Schema<?> schema,String key,int maximum,boolean nullable) {
        Schema<?> property=schema.getProperties().get(key);property.setMaxLength(maximum);property.setNullable(nullable);
    }
}
