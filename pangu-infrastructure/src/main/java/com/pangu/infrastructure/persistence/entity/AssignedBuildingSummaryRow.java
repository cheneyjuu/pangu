package com.pangu.infrastructure.persistence.entity;

public class AssignedBuildingSummaryRow {
    private Long buildingId;
    private Integer unitCount;
    private Double reminderCompletionRate;

    public Long getBuildingId() {
        return buildingId;
    }

    public void setBuildingId(Long buildingId) {
        this.buildingId = buildingId;
    }

    public Integer getUnitCount() {
        return unitCount;
    }

    public void setUnitCount(Integer unitCount) {
        this.unitCount = unitCount;
    }

    public Double getReminderCompletionRate() {
        return reminderCompletionRate;
    }

    public void setReminderCompletionRate(Double reminderCompletionRate) {
        this.reminderCompletionRate = reminderCompletionRate;
    }
}
