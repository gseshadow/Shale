package com.shale.core.model;

import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDateTime;
import java.util.EnumMap;
import org.junit.jupiter.api.Test;

class CompatibilityCaseDateEditorTest {
 @Test void createsExactlyNineConcurrencyAwareIntents() {
  byte[] caseRv={1}, dateRv={2}; var before=new EnumMap<MigratedCaseDateKey,CompatibilityCaseDateState>(MigratedCaseDateKey.class);
  var after=new EnumMap<MigratedCaseDateKey,CompatibilityCaseDateEditor.EditedValue>(MigratedCaseDateKey.class); var old=LocalDateTime.of(2026,1,1,0,0);
  for(var k:MigratedCaseDateKey.values()){before.put(k,new CompatibilityCaseDateState(k,k.systemKey(),null,null,true,null,null,new CompatibilityCaseDateMutation.ExpectedAbsent(caseRv)));after.put(k,null);}
  before.put(MigratedCaseDateKey.DATE_OF_INJURY,new CompatibilityCaseDateState(MigratedCaseDateKey.DATE_OF_INJURY,"date_of_injury",old,null,true,10L,dateRv,null));
  before.put(MigratedCaseDateKey.STATUTE_OF_LIMITATIONS,new CompatibilityCaseDateState(MigratedCaseDateKey.STATUTE_OF_LIMITATIONS,"statute_of_limitations",old,null,true,11L,dateRv,null));
  before.put(MigratedCaseDateKey.TORT_NOTICE_DEADLINE,new CompatibilityCaseDateState(MigratedCaseDateKey.TORT_NOTICE_DEADLINE,"tort_notice_deadline",old,null,true,12L,dateRv,null));
  after.put(MigratedCaseDateKey.DATE_OF_INJURY,new CompatibilityCaseDateEditor.EditedValue(old,null,true));
  after.put(MigratedCaseDateKey.STATUTE_OF_LIMITATIONS,new CompatibilityCaseDateEditor.EditedValue(old.plusDays(1),null,true));
  after.put(MigratedCaseDateKey.CALLER_DATE,new CompatibilityCaseDateEditor.EditedValue(old.withHour(9),null,false));
  var result=CompatibilityCaseDateEditor.mutations(before,after); assertEquals(9,result.size());
  assertInstanceOf(CompatibilityCaseDateMutation.Unchanged.class,result.get(MigratedCaseDateKey.DATE_OF_INJURY));
  var update=assertInstanceOf(CompatibilityCaseDateMutation.Update.class,result.get(MigratedCaseDateKey.STATUTE_OF_LIMITATIONS)); assertEquals(11L,update.occurrenceId());assertArrayEquals(dateRv,update.expectedRowVer());
  var create=assertInstanceOf(CompatibilityCaseDateMutation.Create.class,result.get(MigratedCaseDateKey.CALLER_DATE));assertArrayEquals(caseRv,create.expectedAbsent().observedCaseRowVer());
  var clear=assertInstanceOf(CompatibilityCaseDateMutation.Clear.class,result.get(MigratedCaseDateKey.TORT_NOTICE_DEADLINE));assertEquals(12L,clear.occurrenceId());
 }
}
