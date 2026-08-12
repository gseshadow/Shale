package com.shale.ui.controller.support;

import com.shale.core.dto.EffectiveCaseDateTypeDto;
import com.shale.core.dto.FormConfigurationDto;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class NewIntakeDatesConfigurationTest {
 @Test void missingConfigurationPreservesLegacyBehavior(){assertTrue(NewIntakeDatesConfiguration.renderable(missing(),List.of(type(1,"One"))).isEmpty());}
 @Test void renderingFiltersAndUsesPersistedOrder(){var c=config(List.of(field(10,1,9,true,true,false),field(11,2,2,true,true,true),field(12,3,0,false,true,false),field(13,4,1,true,false,false)));var out=NewIntakeDatesConfiguration.renderable(c,List.of(type(1,"One"),type(2,"Two"),type(3,"Three"),type(4,"Four")));assertEquals(List.of(2,1),out.stream().map(d->d.type().id()).toList());assertTrue(out.getFirst().required());}
 @Test void fieldIdentityUsesIdNotLabel(){String a=NewIntakeDatesConfiguration.draft(List.of(new NewIntakeDatesConfiguration.Selection(type(7,"Original"),false))).fields().getFirst().fieldKey();String b=NewIntakeDatesConfiguration.draft(List.of(new NewIntakeDatesConfiguration.Selection(type(7,"Renamed"),false))).fields().getFirst().fieldKey();assertEquals("case_date:7",a);assertEquals(a,b);}
 @Test void selectionRemovalAndReorderingProduceSequentialOrder(){var d=NewIntakeDatesConfiguration.draft(List.of(new NewIntakeDatesConfiguration.Selection(type(3,"Third"),false),new NewIntakeDatesConfiguration.Selection(type(1,"First"),true)));assertEquals("dates",d.sectionKey());assertEquals(List.of(3,1),d.fields().stream().map(f->f.caseDateTypeId()).toList());assertEquals(List.of(0,1),d.fields().stream().map(f->f.sortOrder()).toList());assertEquals(List.of(false,true),d.fields().stream().map(f->f.required()).toList());}
 @Test void requiredCanChangeIndependentlyWithoutChangingStableIdentity(){var first=new NewIntakeDatesConfiguration.Selection(type(3,"Third"),false);var second=new NewIntakeDatesConfiguration.Selection(type(1,"First"),false);var changed=List.of(NewIntakeDatesConfiguration.withRequired(first,true),second);var d=NewIntakeDatesConfiguration.draft(changed);assertEquals(List.of("case_date:3","case_date:1"),d.fields().stream().map(f->f.fieldKey()).toList());assertEquals(List.of(true,false),d.fields().stream().map(f->f.required()).toList());}
 @Test void newlyAddedSelectionConventionIsOptional(){var added=new NewIntakeDatesConfiguration.Selection(type(4,"Fourth"),false);assertFalse(added.required());}
 private static FormConfigurationDto missing(){return new FormConfigurationDto(0,5,"NEW_INTAKE",List.of(),null,null,null);}
 private static FormConfigurationDto config(List<FormConfigurationDto.Field> f){return new FormConfigurationDto(8,5,"NEW_INTAKE",List.of(new FormConfigurationDto.Section(2,"dates","Dates",0,true,true,f)),null,null,new byte[]{1});}
 private static FormConfigurationDto.Field field(long id,int type,int order,boolean enabled,boolean visible,boolean required){return new FormConfigurationDto.Field(id,"case_date:"+type,"CASE_DATE",type,order,enabled,visible,required);}
 private static EffectiveCaseDateTypeDto type(int id,String name){return new EffectiveCaseDateTypeDto(id,null,"type_"+id,name,null,"OTHER",null,false,id,true,false,EffectiveCaseDateTypeDto.Origin.GLOBAL,new byte[]{1});}
}
