/*
 * Airways project (C) Alexey Kornev, 2015-2018
 */

/*
 * Airways project (C) Alexey Kornev, 2015-2018
 */

package net.simforge.airways.engine.entities;

import net.simforge.commons.hibernate.BaseEntity;

import javax.persistence.*;

@Entity(name = "EngineTask")
@Table(name = "engine_task")
public class TaskEntity implements BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "engine_task_id_seq")
    @SequenceGenerator(name = "engine_task_id_seq", sequenceName = "engine_task_id_seq", allocationSize = 1)
    private Integer id;
    @Version
    private Integer version;

    private Integer status;
    private Integer retryCount;
    private Long taskTime;
    private String processorClassName;
    private String entityClassName;
    private Integer entityId;

    @Override
    public Integer getId() {
        return id;
    }

    @Override
    public void setId(Integer id) {
        this.id = id;
    }

    @Override
    public Integer getVersion() {
        return version;
    }

    @Override
    public void setVersion(Integer version) {
        this.version = version;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public Long getTaskTime() {
        return taskTime;
    }

    public void setTaskTime(Long taskTime) {
        this.taskTime = taskTime;
    }

    public String getProcessorClassName() {
        return processorClassName;
    }

    public void setProcessorClassName(String processorClassName) {
        this.processorClassName = processorClassName;
    }

    public String getEntityClassName() {
        return entityClassName;
    }

    public void setEntityClassName(String entityClassName) {
        this.entityClassName = entityClassName;
    }

    public Integer getEntityId() {
        return entityId;
    }

    public void setEntityId(Integer entityId) {
        this.entityId = entityId;
    }

    public static class Status {
        public static final int ACTIVE = 0;
        public static final int DONE = 100;
        public static final int FAILED = 99;
    }

}
