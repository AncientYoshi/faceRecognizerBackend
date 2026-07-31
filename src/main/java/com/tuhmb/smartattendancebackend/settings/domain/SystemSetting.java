package com.tuhmb.smartattendancebackend.settings.domain;

import com.tuhmb.smartattendancebackend.common.domain.BaseEntity;
import com.tuhmb.smartattendancebackend.user.domain.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "system_settings")
public class SystemSetting extends BaseEntity {

    @Column(name = "setting_key", nullable = false, unique = true, length = 80)
    private String key;

    @Column(name = "setting_value", nullable = false, length = 500)
    private String value;

    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", nullable = false, length = 20)
    private SettingValueType valueType;

    @Column(length = 500)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private AppUser updatedBy;

    protected SystemSetting() {
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public SettingValueType getValueType() {
        return valueType;
    }

    public String getDescription() {
        return description;
    }

    public AppUser getUpdatedBy() {
        return updatedBy;
    }

    public void updateValue(String value, AppUser updatedBy) {
        this.value = value;
        this.updatedBy = updatedBy;
    }
}
