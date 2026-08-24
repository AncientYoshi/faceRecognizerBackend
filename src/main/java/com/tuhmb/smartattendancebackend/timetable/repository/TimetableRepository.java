package com.tuhmb.smartattendancebackend.timetable.repository;

import com.tuhmb.smartattendancebackend.timetable.domain.TimetableEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public interface TimetableRepository
        extends JpaRepository<TimetableEntry, UUID> {

    @EntityGraph(attributePaths = "course")
    @Query("""
            SELECT timetable
            FROM TimetableEntry timetable
            WHERE (:courseId IS NULL
                   OR timetable.course.id = :courseId)
              AND (:dayOfWeek IS NULL
                   OR timetable.dayOfWeek = :dayOfWeek)
              AND (:active IS NULL
                   OR timetable.active = :active)
              AND (
                    :onDate IS NULL
                    OR (
                        (timetable.effectiveFrom IS NULL
                         OR timetable.effectiveFrom <= :onDate)
                        AND
                        (timetable.effectiveTo IS NULL
                         OR timetable.effectiveTo >= :onDate)
                    )
                  )
            """)
    Page<TimetableEntry> search(
            @Param("courseId") UUID courseId,
            @Param("dayOfWeek") DayOfWeek dayOfWeek,
            @Param("onDate") LocalDate onDate,
            @Param("active") Boolean active,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"course", "course.teacher", "course.teacher.user"})
    @Query("""
            SELECT timetable
            FROM TimetableEntry timetable
            WHERE timetable.active = TRUE
              AND EXISTS (
                    SELECT enrollment.id
                    FROM Enrollment enrollment
                    WHERE enrollment.course = timetable.course
                      AND enrollment.student.id = :studentId
                  )
              AND (
                    timetable.effectiveFrom IS NULL
                    OR timetable.effectiveFrom <= :weekEnd
                  )
              AND (
                    timetable.effectiveTo IS NULL
                    OR timetable.effectiveTo >= :weekStart
                  )
            ORDER BY timetable.dayOrder, timetable.startTime, timetable.course.code
            """)
    List<TimetableEntry> findStudentEntriesForWeek(
            @Param("studentId") UUID studentId,
            @Param("weekStart") LocalDate weekStart,
            @Param("weekEnd") LocalDate weekEnd
    );

    @EntityGraph(attributePaths = {"course", "course.teacher", "course.teacher.user", "course.department"})
    @Query("""
            SELECT timetable
            FROM TimetableEntry timetable
            WHERE timetable.active = TRUE
              AND timetable.dayOfWeek = :dayOfWeek
              AND (timetable.effectiveFrom IS NULL OR timetable.effectiveFrom <= :date)
              AND (timetable.effectiveTo IS NULL OR timetable.effectiveTo >= :date)
            ORDER BY timetable.startTime, timetable.course.code
            """)
    List<TimetableEntry> findActiveEntriesForDate(
            @Param("dayOfWeek") DayOfWeek dayOfWeek,
            @Param("date") LocalDate date
    );

    @Query("""
            SELECT COALESCE(SUM(timetable.rollCallCount), 0)
            FROM TimetableEntry timetable
            WHERE timetable.course.department.id = :departmentId
              AND timetable.course.studyYear = :studyYear
              AND timetable.dayOfWeek = :dayOfWeek
              AND timetable.active = TRUE
              AND (:excludedId IS NULL OR timetable.id <> :excludedId)
              AND (
                    :effectiveTo IS NULL
                    OR timetable.effectiveFrom IS NULL
                    OR timetable.effectiveFrom <= :effectiveTo
                  )
              AND (
                    :effectiveFrom IS NULL
                    OR timetable.effectiveTo IS NULL
                    OR timetable.effectiveTo >= :effectiveFrom
                  )
            """)
    long sumCohortRollCallsForPeriod(
            @Param("departmentId") UUID departmentId,
            @Param("studyYear") Integer studyYear,
            @Param("dayOfWeek") DayOfWeek dayOfWeek,
            @Param("effectiveFrom") LocalDate effectiveFrom,
            @Param("effectiveTo") LocalDate effectiveTo,
            @Param("excludedId") UUID excludedId
    );

    @Query("""
        SELECT COUNT(timetable)
        FROM TimetableEntry timetable
        WHERE timetable.course.id = :courseId
          AND timetable.dayOfWeek = :dayOfWeek
          AND timetable.active = TRUE
          AND timetable.startTime < :endTime
          AND timetable.endTime > :startTime
          AND (
                cast(:effectiveTo as LocalDate) IS NULL
                OR timetable.effectiveFrom IS NULL
                OR timetable.effectiveFrom
                    <= cast(:effectiveTo as LocalDate)
              )
          AND (
                cast(:effectiveFrom as LocalDate) IS NULL
                OR timetable.effectiveTo IS NULL
                OR timetable.effectiveTo
                    >= cast(:effectiveFrom as LocalDate)
              )
        """)
    long countCourseConflicts(
            @Param("courseId") UUID courseId,
            @Param("dayOfWeek") DayOfWeek dayOfWeek,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime,
            @Param("effectiveFrom") LocalDate effectiveFrom,
            @Param("effectiveTo") LocalDate effectiveTo
    );

    @Query("""
            SELECT COUNT(timetable)
            FROM TimetableEntry timetable
            WHERE timetable.id <> :excludedId
              AND timetable.course.id = :courseId
              AND timetable.dayOfWeek = :dayOfWeek
              AND timetable.active = TRUE
              AND timetable.startTime < :endTime
              AND timetable.endTime > :startTime
              AND (
                    :effectiveTo IS NULL
                    OR timetable.effectiveFrom IS NULL
                    OR timetable.effectiveFrom <= :effectiveTo
                  )
              AND (
                    :effectiveFrom IS NULL
                    OR timetable.effectiveTo IS NULL
                    OR timetable.effectiveTo >= :effectiveFrom
                  )
            """)
    long countCourseConflictsExcluding(
            @Param("excludedId") UUID excludedId,
            @Param("courseId") UUID courseId,
            @Param("dayOfWeek") DayOfWeek dayOfWeek,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime,
            @Param("effectiveFrom") LocalDate effectiveFrom,
            @Param("effectiveTo") LocalDate effectiveTo
    );

    @Query("""
        SELECT COUNT(timetable)
        FROM TimetableEntry timetable
        WHERE LOWER(timetable.room) = LOWER(:room)
          AND timetable.dayOfWeek = :dayOfWeek
          AND timetable.active = TRUE
          AND timetable.startTime < :endTime
          AND timetable.endTime > :startTime
          AND (
                CAST(:effectiveTo AS LocalDate) IS NULL
                OR timetable.effectiveFrom IS NULL
                OR timetable.effectiveFrom
                    <= CAST(:effectiveTo AS LocalDate)
              )
          AND (
                CAST(:effectiveFrom AS LocalDate) IS NULL
                OR timetable.effectiveTo IS NULL
                OR timetable.effectiveTo
                    >= CAST(:effectiveFrom AS LocalDate)
              )
        """)
    long countRoomConflicts(
            @Param("room") String room,
            @Param("dayOfWeek") DayOfWeek dayOfWeek,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime,
            @Param("effectiveFrom") LocalDate effectiveFrom,
            @Param("effectiveTo") LocalDate effectiveTo
    );

    @Query("""
        SELECT COUNT(timetable)
        FROM TimetableEntry timetable
        WHERE timetable.id <> :excludedId
          AND LOWER(timetable.room) = LOWER(:room)
          AND timetable.dayOfWeek = :dayOfWeek
          AND timetable.active = TRUE
          AND timetable.startTime < :endTime
          AND timetable.endTime > :startTime
          AND (
                CAST(:effectiveTo AS LocalDate) IS NULL
                OR timetable.effectiveFrom IS NULL
                OR timetable.effectiveFrom
                    <= CAST(:effectiveTo AS LocalDate)
              )
          AND (
                CAST(:effectiveFrom AS LocalDate) IS NULL
                OR timetable.effectiveTo IS NULL
                OR timetable.effectiveTo
                    >= CAST(:effectiveFrom AS LocalDate)
              )
        """)
    long countRoomConflictsExcluding(
            @Param("excludedId") UUID excludedId,
            @Param("room") String room,
            @Param("dayOfWeek") DayOfWeek dayOfWeek,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime,
            @Param("effectiveFrom") LocalDate effectiveFrom,
            @Param("effectiveTo") LocalDate effectiveTo
    );
}
