package com.shale.core.service;

import java.util.Arrays;
import com.shale.core.model.TaskDueDatePolicy;

/** Authoritative administration boundary for the tenant-wide task policy. */
public interface TaskPolicyConfigurationServicePort {
    TaskPolicyConfiguration load(int shaleClientId, int actorUserId);
    TaskPolicyConfiguration update(UpdateTaskPolicyCommand command);
    record TaskPolicyConfiguration(long id,int shaleClientId,TaskDueDatePolicy dueDatePolicy,byte[] rowVer){public TaskPolicyConfiguration{rowVer=copy(rowVer);}@Override public byte[] rowVer(){return copy(rowVer);}}
    record UpdateTaskPolicyCommand(int shaleClientId,int actorUserId,long configurationId,TaskDueDatePolicy dueDatePolicy,byte[] expectedRowVer){public UpdateTaskPolicyCommand{if(expectedRowVer==null||expectedRowVer.length==0)throw new IllegalArgumentException("expectedRowVer is required.");expectedRowVer=copy(expectedRowVer);}@Override public byte[] expectedRowVer(){return copy(expectedRowVer);}}
    private static byte[] copy(byte[] value){return value==null?null:Arrays.copyOf(value,value.length);}
}
