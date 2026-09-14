package com.shale.ui.controller;

import com.shale.core.dto.EffectiveCaseDateTypeDto;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** UI-free staged state. No operation here can persist a Case Overview change. */
public final class CaseOverviewEditModel {
    private final List<Integer> baselineOrder;
    private final Integer baselineIntakeUserId;
    private final Map<Integer, EffectiveCaseDateTypeDto> types;
    private final Set<Integer> effectiveTypeIds;
    private final List<Integer> selected;
    private Integer intakeUserId;
    private final AtomicBoolean saving = new AtomicBoolean();

    public CaseOverviewEditModel(List<EffectiveCaseDateTypeDto> available, List<EffectiveCaseDateTypeDto> baseline, Integer intakeUserId) {
        types = new LinkedHashMap<>();
        if (available != null) available.forEach(t -> types.put(t.id(), t));
        effectiveTypeIds = available == null ? Set.of() : available.stream().map(EffectiveCaseDateTypeDto::id).collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (baseline != null) baseline.forEach(t -> types.putIfAbsent(t.id(), t));
        baselineOrder = baseline == null ? List.of() : baseline.stream().map(EffectiveCaseDateTypeDto::id).toList();
        selected = new ArrayList<>(baselineOrder);
        baselineIntakeUserId = intakeUserId;
        this.intakeUserId = intakeUserId;
    }
    public List<EffectiveCaseDateTypeDto> allTypes(){return List.copyOf(types.values());}
    /** The authoritative top-to-bottom order rendered and submitted by the editor. */
    public List<EffectiveCaseDateTypeDto> shownTypes(){return selected.stream().map(types::get).filter(Objects::nonNull).toList();}
    /** Effective, unselected choices in a stable presentation order. */
    public List<EffectiveCaseDateTypeDto> availableTypes(){return types.values().stream().filter(t->effectiveTypeIds.contains(t.id())&&!selected.contains(t.id())).sorted(Comparator.comparingInt(EffectiveCaseDateTypeDto::sortOrder).thenComparing(EffectiveCaseDateTypeDto::name,String.CASE_INSENSITIVE_ORDER).thenComparingInt(EffectiveCaseDateTypeDto::id)).toList();}
    public List<Integer> selectedIds(){return List.copyOf(selected);}
    public Integer intakeUserId(){return intakeUserId;}
    public void setIntakeUserId(Integer id){intakeUserId=id;}
    public boolean selected(int id){return selected.contains(id);}
    public void select(int id){if(types.containsKey(id)&&!selected.contains(id))selected.add(id);}
    public void remove(int id){selected.remove(Integer.valueOf(id));}
    public boolean moveUp(int id){int i=selected.indexOf(id);if(i<=0)return false;java.util.Collections.swap(selected,i,i-1);return true;}
    public boolean moveDown(int id){int i=selected.indexOf(id);if(i<0||i==selected.size()-1)return false;java.util.Collections.swap(selected,i,i+1);return true;}
    public boolean canMoveUp(int id){return selected.indexOf(id)>0;}
    public boolean canMoveDown(int id){int i=selected.indexOf(id);return i>=0&&i<selected.size()-1;}
    public boolean layoutChanged(){return !baselineOrder.equals(selected);}
    public boolean intakeChanged(){return !Objects.equals(baselineIntakeUserId,intakeUserId);}
    public boolean changed(){return layoutChanged()||intakeChanged();}
    public boolean beginSave(){return changed()&&saving.compareAndSet(false,true);}
    public void saveFailed(){saving.set(false);}
    public boolean saving(){return saving.get();}
}
