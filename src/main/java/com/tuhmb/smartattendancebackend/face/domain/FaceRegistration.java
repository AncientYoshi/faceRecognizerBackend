package com.tuhmb.smartattendancebackend.face.domain;

import com.tuhmb.smartattendancebackend.common.domain.BaseEntity;
import com.tuhmb.smartattendancebackend.user.domain.Student;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "face_registrations")
public class FaceRegistration extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false, unique = true)
    private Student student;

    @Column(name = "embedding_id", nullable = false, unique = true)
    private String embeddingId;

    protected FaceRegistration() {
    }

    public FaceRegistration(Student student, String embeddingId) {
        this.student = student;
        this.embeddingId = embeddingId;
    }

    public Student getStudent() {
        return student;
    }

    public String getEmbeddingId() {
        return embeddingId;
    }

    public void updateEmbeddingId(String embeddingId) {
        this.embeddingId = embeddingId;
    }
}
