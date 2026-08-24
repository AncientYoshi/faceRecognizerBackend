package com.tuhmb.smartattendancebackend.hardware.domain;

import com.tuhmb.smartattendancebackend.academic.domain.Course;
import com.tuhmb.smartattendancebackend.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "hardware_devices")
public class HardwareDevice extends BaseEntity {

    @Column(name = "device_id", nullable = false, unique = true, length = 100)
    private String deviceId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "key_hash", nullable = false, length = 255)
    private String keyHash;

    @Column(length = 100)
    private String room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Course course;

    @Column(nullable = false)
    private boolean enabled = true;

    protected HardwareDevice() {
    }

    public HardwareDevice(
            String deviceId,
            String name,
            String keyHash,
            String room,
            Course course,
            boolean enabled
    ) {
        this.deviceId = deviceId;
        this.keyHash = keyHash;
        update(name, room, course, enabled);
    }

    public void update(String name, String room, Course course, boolean enabled) {
        this.name = name;
        this.room = room;
        this.course = course;
        this.enabled = enabled;
    }

    public void rotateKey(String keyHash) {
        this.keyHash = keyHash;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getName() {
        return name;
    }

    public String getKeyHash() {
        return keyHash;
    }

    public String getRoom() {
        return room;
    }

    public Course getCourse() {
        return course;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
