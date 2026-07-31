package com.tuhmb.smartattendancebackend.academic.domain;

import com.tuhmb.smartattendancebackend.common.domain.BaseEntity;
import com.tuhmb.smartattendancebackend.user.domain.Teacher;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "courses")
public class Course extends BaseEntity {

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 40)
    private String semester;

    @Column(name = "academic_year", nullable = false, length = 20)
    private String academicYear;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    private Teacher teacher;

    protected Course() {
    }

    public Course(
            String code,
            String name,
            String semester,
            String academicYear,
            Department department,
            Teacher teacher
    ) {
        update(code, name, semester, academicYear, department, teacher);
    }

    public void update(
            String code,
            String name,
            String semester,
            String academicYear,
            Department department,
            Teacher teacher
    ) {
        this.code = code;
        this.name = name;
        this.semester = semester;
        this.academicYear = academicYear;
        this.department = department;
        this.teacher = teacher;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getSemester() {
        return semester;
    }

    public String getAcademicYear() {
        return academicYear;
    }

    public Department getDepartment() {
        return department;
    }

    public Teacher getTeacher() {
        return teacher;
    }
}
