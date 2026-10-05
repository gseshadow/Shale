package com.shale.data.service.adapter;
import java.util.Objects;
import com.shale.core.service.TaskPolicyConfigurationServicePort;
import com.shale.data.dao.TaskPolicyConfigurationDao;
public final class TaskPolicyConfigurationServiceAdapter implements TaskPolicyConfigurationServicePort{
 private final TaskPolicyConfigurationDao dao;public TaskPolicyConfigurationServiceAdapter(TaskPolicyConfigurationDao dao){this.dao=Objects.requireNonNull(dao,"dao");}
 @Override public TaskPolicyConfiguration load(int tenant,int actor){return dao.load(tenant,actor);}
 @Override public TaskPolicyConfiguration update(UpdateTaskPolicyCommand command){return dao.update(command);}
}
