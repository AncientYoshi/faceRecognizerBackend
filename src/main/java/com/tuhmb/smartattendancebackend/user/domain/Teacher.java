package com.tuhmb.smartattendancebackend.user.domain;

import com.tuhmb.smartattendancebackend.academic.domain.Department;
import com.tuhmb.smartattendancebackend.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "teachers")
public class Teacher extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private AppUser user;

    @Column(name = "employee_number", nullable = false, unique = true, length = 80)
    private String employeeNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    protected Teacher() {
    }

    public Teacher(AppUser user, String employeeNumber) {
        this.user = user;
        this.employeeNumber = employeeNumber;
    }

    public AppUser getUser() {
        return user;
    }

    public String getEmployeeNumber() {
        return employeeNumber;
    }

    public void updateEmployeeNumber(String employeeNumber) {
        this.employeeNumber = employeeNumber;
    }

    public Department getDepartment() {
        return department;
    }

    public void assignDepartment(Department department) {
        this.department = department;
    }
}
