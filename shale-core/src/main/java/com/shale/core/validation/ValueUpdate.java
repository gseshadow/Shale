package com.shale.core.validation;

/** PATCH intent. Retain is an operation, never an assertion that a submitted value is unchanged. */
public record ValueUpdate(Action action,String value,String extension) {
    public enum Action { RETAIN, SET, CLEAR }
    public ValueUpdate {
        if(action==null)throw new IllegalArgumentException("A field update action is required.");
        if(action!=Action.SET&&(value!=null||extension!=null))throw new IllegalArgumentException("Retain and clear cannot contain values.");
        if(action==Action.SET&&(value==null||value.isBlank())&&(extension==null||extension.isBlank()))throw new IllegalArgumentException("Set requires a value. Use clear to remove an optional field.");
    }
    public static ValueUpdate retain(){return new ValueUpdate(Action.RETAIN,null,null);}
    public boolean retained(){return action==Action.RETAIN;}
    public String input(){return action==Action.CLEAR?null:value;}
}
