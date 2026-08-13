package com.shale.ui.controller.support;

import com.shale.core.dto.EffectiveCaseDateTypeDto;
import com.shale.core.dto.FormConfigurationDto;
import java.util.List;
import java.util.HashMap;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class NewIntakeDatesConfigurationTest {
 @Test void missingConfigurationUsesOnlyEffectiveTypesInAuthoritativeOrder(){var out=NewIntakeDatesConfiguration.renderable(missing(),List.of(type(3,"Intake",null,true,false),type(7,"Statute",5,true,false),type(9,"Tort",5,true,false)));assertEquals(List.of(3,7,9),out.stream().map(d->d.type().id()).toList());assertTrue(out.stream().noneMatch(NewIntakeDatesConfiguration.ConfiguredDate::required));}
 @Test void initialRenderingAndCustomizationShareTheSameTenantScopedTypes(){var types=List.of(type(3,"Intake",null,true,false),type(7,"Statute",5,true,false),type(9,"Tort",5,true,false));assertEquals(NewIntakeDatesConfiguration.renderable(missing(),types).stream().map(d->d.type().id()).toList(),NewIntakeDatesConfiguration.selections(missing(),types).stream().map(s->s.type().id()).toList());}
 @Test void explicitlySavedEmptyConfigurationRemainsEmpty(){assertTrue(NewIntakeDatesConfiguration.renderable(config(List.of()),List.of(type(1,"One",null,true,false))).isEmpty());assertTrue(NewIntakeDatesConfiguration.selections(config(List.of()),List.of(type(1,"One",null,true,false))).isEmpty());}
 @Test void renderingFiltersInactiveDeletedMissingAndCrossTenantTypesAndPreservesSavedOrder(){var c=config(List.of(field(10,1,9),field(11,2,2),field(12,3,0),field(13,4,1),field(14,99,3)));var active=List.of(type(1,"Own",5,true,false),type(2,"Global",null,true,false),type(3,"Inactive",5,false,false),type(4,"Deleted",5,true,true),type(99,"Foreign",6,true,false));var out=NewIntakeDatesConfiguration.renderable(c,active);assertEquals(List.of(2,1),out.stream().map(d->d.type().id()).toList());}
 @Test void fieldIdentityUsesIdNotLabel(){String a=NewIntakeDatesConfiguration.draft(List.of(new NewIntakeDatesConfiguration.Selection(type(7,"Original",null,true,false),false))).fields().getFirst().fieldKey();assertEquals("case_date:7",a);}
 @Test void onlySemanticIntakeMappingDefaultsAndMappingMayUseAnyIdOrLabel(){LocalDate today=LocalDate.of(2026,8,13);assertEquals(today,NewIntakeDatesConfiguration.initialValue("case_date:731",731,731,today,new HashMap<>()));assertNull(NewIntakeDatesConfiguration.initialValue("case_date:3",3,731,today,new HashMap<>()));}
 @Test void preservedUserValueIncludingExplicitEmptySurvivesRerender(){LocalDate today=LocalDate.of(2026,8,13),entered=LocalDate.of(2025,2,4);var values=new HashMap<String,LocalDate>();values.put("case_date:731",entered);assertEquals(entered,NewIntakeDatesConfiguration.initialValue("case_date:731",731,731,today,values));values.put("case_date:731",null);assertNull(NewIntakeDatesConfiguration.initialValue("case_date:731",731,731,today,values));}
 @Test void readdingUnsavedIntakeWithoutPreservedValueRestoresDefault(){LocalDate today=LocalDate.of(2026,8,13);assertEquals(today,NewIntakeDatesConfiguration.initialValue("case_date:44",44,44,today,new HashMap<>()));}
 private static FormConfigurationDto missing(){return new FormConfigurationDto(0,5,"NEW_INTAKE",List.of(),null,null,null);}
 private static FormConfigurationDto config(List<FormConfigurationDto.Field> f){return new FormConfigurationDto(8,5,"NEW_INTAKE",List.of(new FormConfigurationDto.Section(2,"dates","Dates",0,true,true,f)),null,null,new byte[]{1});}
 private static FormConfigurationDto.Field field(long id,int type,int order){return new FormConfigurationDto.Field(id,"case_date:"+type,"CASE_DATE",type,order,true,true,false);}
 private static EffectiveCaseDateTypeDto type(int id,String name,Integer tenant,boolean active,boolean deleted){return new EffectiveCaseDateTypeDto(id,tenant,"type_"+id,name,null,"OTHER",null,false,id,active,deleted,tenant==null?EffectiveCaseDateTypeDto.Origin.GLOBAL:EffectiveCaseDateTypeDto.Origin.TENANT_CREATED,new byte[]{1});}
}
