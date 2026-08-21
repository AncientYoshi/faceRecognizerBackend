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
@Table(name = "students")
public class Student extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private AppUser user;

    @Column(name = "student_number", nullable = false, unique = true, length = 80)
    private String studentNumber;

    @Column(name = "study_year")
    private Integer studyYear;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    protected Student() {
    }

    public Student(AppUser user, String studentNumber) {
        this(user, studentNumber, null);
    }

    public Student(AppUser user, String studentNumber, Integer studyYear) {
        this.user = user;
        this.studentNumber = studentNumber;
        this.studyYear = studyYear;
    }

    public AppUser getUser() {
        return user;
    }

    public String getStudentNumber() {
        return studentNumber;
    }

    public void updateStudentNumber(String studentNumber) {
        this.studentNumber = studentNumber;
    }

    public Integer getStudyYear() {
        return studyYear;
    }

    public void updateStudyYear(Integer studyYear) {
        this.studyYear = studyYear;
    }

    public Department getDepartment() {
        return department;
    }

    public void assignDepartment(Department department) {
        this.department = department;
    }
}
