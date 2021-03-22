package org.synyx.urlaubsverwaltung.calendar;

public interface DepartmentCalendarService {

    /**
     * Deletes all department calendars for the given department id
     *
     * @param departmentId to delete all calendars of the departments
     */
    void deleteAllDepartmentsCalendarsForDepartment(int departmentId);
}
