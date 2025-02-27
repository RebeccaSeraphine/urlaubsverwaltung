package org.synyx.urlaubsverwaltung.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.synyx.urlaubsverwaltung.absence.Absence;
import org.synyx.urlaubsverwaltung.absence.AbsenceMapping;
import org.synyx.urlaubsverwaltung.absence.AbsenceMappingService;
import org.synyx.urlaubsverwaltung.absence.TimeSettings;
import org.synyx.urlaubsverwaltung.account.AccountInteractionService;
import org.synyx.urlaubsverwaltung.application.domain.Application;
import org.synyx.urlaubsverwaltung.application.domain.ApplicationComment;
import org.synyx.urlaubsverwaltung.application.domain.ApplicationCommentAction;
import org.synyx.urlaubsverwaltung.application.domain.ApplicationStatus;
import org.synyx.urlaubsverwaltung.calendarintegration.CalendarSyncService;
import org.synyx.urlaubsverwaltung.department.DepartmentService;
import org.synyx.urlaubsverwaltung.period.DayLength;
import org.synyx.urlaubsverwaltung.person.Person;
import org.synyx.urlaubsverwaltung.person.Role;
import org.synyx.urlaubsverwaltung.settings.Settings;
import org.synyx.urlaubsverwaltung.settings.SettingsService;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static java.time.ZoneOffset.UTC;
import static java.util.Arrays.asList;
import static java.util.Optional.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.synyx.urlaubsverwaltung.TestDataCreator.anyAbsenceMapping;
import static org.synyx.urlaubsverwaltung.TestDataCreator.createApplication;
import static org.synyx.urlaubsverwaltung.TestDataCreator.createPerson;
import static org.synyx.urlaubsverwaltung.TestDataCreator.createVacationType;
import static org.synyx.urlaubsverwaltung.absence.AbsenceMappingType.VACATION;
import static org.synyx.urlaubsverwaltung.application.domain.ApplicationCommentAction.CANCELLED;
import static org.synyx.urlaubsverwaltung.application.domain.ApplicationCommentAction.CANCEL_REQUESTED;
import static org.synyx.urlaubsverwaltung.application.domain.ApplicationCommentAction.CANCEL_REQUESTED_DECLINED;
import static org.synyx.urlaubsverwaltung.application.domain.ApplicationCommentAction.CONVERTED;
import static org.synyx.urlaubsverwaltung.application.domain.ApplicationCommentAction.EDITED;
import static org.synyx.urlaubsverwaltung.application.domain.ApplicationCommentAction.REFERRED;
import static org.synyx.urlaubsverwaltung.application.domain.ApplicationStatus.ALLOWED;
import static org.synyx.urlaubsverwaltung.application.domain.ApplicationStatus.ALLOWED_CANCELLATION_REQUESTED;
import static org.synyx.urlaubsverwaltung.application.domain.ApplicationStatus.WAITING;
import static org.synyx.urlaubsverwaltung.application.domain.VacationCategory.HOLIDAY;
import static org.synyx.urlaubsverwaltung.person.Role.DEPARTMENT_HEAD;
import static org.synyx.urlaubsverwaltung.person.Role.OFFICE;
import static org.synyx.urlaubsverwaltung.person.Role.SECOND_STAGE_AUTHORITY;
import static org.synyx.urlaubsverwaltung.person.Role.USER;

@ExtendWith(MockitoExtension.class)
class ApplicationInteractionServiceImplTest {

    private ApplicationInteractionServiceImpl sut;

    @Mock
    private ApplicationService applicationService;
    @Mock
    private ApplicationCommentService commentService;
    @Mock
    private AccountInteractionService accountInteractionService;
    @Mock
    private ApplicationMailService applicationMailService;
    @Mock
    private CalendarSyncService calendarSyncService;
    @Mock
    private AbsenceMappingService absenceMappingService;
    @Mock
    private SettingsService settingsService;
    @Mock
    private DepartmentService departmentService;

    private final Clock clock = Clock.systemUTC();

    @BeforeEach
    void setUp() {

        final Settings settings = new Settings();
        settings.setTimeSettings(new TimeSettings());
        when(settingsService.getSettings()).thenReturn(settings);

        sut = new ApplicationInteractionServiceImpl(applicationService, commentService, accountInteractionService,
            applicationMailService, calendarSyncService, absenceMappingService, settingsService, departmentService, clock);
    }

    // APPLY FOR LEAVE -------------------------------------------------------------------------------------------------
    @Test
    void ensureApplyForLeaveChangesStateAndOtherAttributesAndSavesTheApplicationForLeave() {

        when(calendarSyncService.addAbsence(any(Absence.class))).thenReturn(of("42"));

        Person person = new Person("muster", "Muster", "Marlene", "muster@example.org");
        Person applier = new Person("muster", "Muster", "Marlene", "muster@example.org");
        Optional<String> comment = of("Foo");

        Application applicationForLeave = getDummyApplication(person);
        when(applicationService.save(applicationForLeave)).thenReturn(applicationForLeave);

        sut.apply(applicationForLeave, applier, comment);

        assertThat(applicationForLeave.getStatus()).isEqualTo(WAITING);
        assertThat(applicationForLeave.getPerson()).isEqualTo(person);
        assertThat(applicationForLeave.getApplier()).isEqualTo(applier);
        assertThat(applicationForLeave.getApplicationDate()).isEqualTo(LocalDate.now(UTC));

        verify(applicationService).save(applicationForLeave);
        verify(commentService).create(eq(applicationForLeave), eq(ApplicationCommentAction.APPLIED), eq(comment), eq(applier));
    }

    @Test
    void ensureApplyingForLeaveAddsCalendarEvent() {

        when(calendarSyncService.addAbsence(any(Absence.class))).thenReturn(of("42"));

        Person person = new Person("muster", "Muster", "Marlene", "muster@example.org");
        Person applier = new Person("muster", "Muster", "Marlene", "muster@example.org");
        Optional<String> comment = of("Foo");

        Application applicationForLeave = getDummyApplication(person);
        when(applicationService.save(applicationForLeave)).thenReturn(applicationForLeave);

        sut.apply(applicationForLeave, applier, comment);

        verify(calendarSyncService).addAbsence(any(Absence.class));
        verify(absenceMappingService).create(isNull(), eq(VACATION), anyString());
    }

    @Test
    void ensureSendsConfirmationEmailToPersonAndNotificationEmailToBossesWhenApplyingForOneself() {

        when(calendarSyncService.addAbsence(any(Absence.class))).thenReturn(of("42"));

        Person person = new Person("muster", "Muster", "Marlene", "muster@example.org");

        final Application applicationForLeave = getDummyApplication(person);
        when(applicationService.save(applicationForLeave)).thenReturn(applicationForLeave);

        ApplicationComment applicationComment = new ApplicationComment(person, clock);
        when(commentService.create(eq(applicationForLeave), eq(ApplicationCommentAction.APPLIED), any(), eq(person))).thenReturn(applicationComment);

        sut.apply(applicationForLeave, person, of("Foo"));

        verify(applicationMailService).sendConfirmation(eq(applicationForLeave), eq(applicationComment));
        verify(applicationMailService, never()).sendAppliedForLeaveByOfficeNotification(eq(applicationForLeave), any(ApplicationComment.class));
        verify(applicationMailService).sendNewApplicationNotification(eq(applicationForLeave), eq(applicationComment));
    }

    @Test
    void ensureSendsNotificationToPersonIfApplicationForLeaveNotAppliedByOneself() {

        when(calendarSyncService.addAbsence(any(Absence.class))).thenReturn(of("42"));

        final Person person = new Person("muster", "Muster", "Marlene", "muster@example.org");
        final Person applier = new Person("muster", "Muster", "Marlene", "muster@example.org");
        applier.setPermissions(List.of(OFFICE));

        Application applicationForLeave = getDummyApplication(person);
        when(applicationService.save(applicationForLeave)).thenReturn(applicationForLeave);

        ApplicationComment applicationComment = new ApplicationComment(person, clock);
        when(commentService.create(eq(applicationForLeave), eq(ApplicationCommentAction.APPLIED), any(), eq(applier))).thenReturn(applicationComment);

        sut.apply(applicationForLeave, applier, of("Foo"));

        verify(applicationMailService, never()).sendConfirmation(eq(applicationForLeave), any(ApplicationComment.class));
        verify(applicationMailService).sendAppliedForLeaveByOfficeNotification(eq(applicationForLeave), eq(applicationComment));
        verify(applicationMailService).sendNewApplicationNotification(eq(applicationForLeave), eq(applicationComment));
    }

    @Test
    void ensureApplyingForLeaveUpdatesTheRemainingVacationDays() {

        when(calendarSyncService.addAbsence(any(Absence.class))).thenReturn(of("42"));

        final Person person = new Person("muster", "Muster", "Marlene", "muster@example.org");
        final Person applier = new Person("muster", "Muster", "Marlene", "muster@example.org");
        final Optional<String> comment = of("Foo");

        final Application applicationForLeave = getDummyApplication(person);
        when(applicationService.save(applicationForLeave)).thenReturn(applicationForLeave);

        sut.apply(applicationForLeave, applier, comment);

        verify(accountInteractionService).updateRemainingVacationDays(2013, person);
    }

    // ALLOW APPLICATION FOR LEAVE -------------------------------------------------------------------------------------
    // ALLOWING - BOSS
    @Test
    void ensureWaitingApplicationForLeaveCanBeAllowedByBoss() {

        Person person = new Person("muster", "Muster", "Marlene", "muster@example.org");
        Person boss = createPerson("boss", USER, Role.BOSS);
        Optional<String> comment = of("Foo");

        final Application applicationForLeave = getDummyApplication(person);
        applicationForLeave.setStatus(WAITING);
        when(applicationService.save(applicationForLeave)).thenReturn(applicationForLeave);

        when(commentService.create(applicationForLeave, ApplicationCommentAction.ALLOWED, comment, boss)).thenReturn(new ApplicationComment(person, clock));

        sut.allow(applicationForLeave, boss, comment);

        assertApplicationForLeaveHasChangedStatus(applicationForLeave, ALLOWED, person, boss);
        assertApplicationForLeaveAndCommentAreSaved(applicationForLeave, ApplicationCommentAction.ALLOWED, comment, boss);
        assertNoCalendarSyncIsExecuted();
        assertAllowedNotificationIsSent(applicationForLeave);
    }

    @Test
    void ensureTemporaryAllowedApplicationForLeaveCanBeAllowedByBoss() {

        Person person = new Person("muster", "Muster", "Marlene", "muster@example.org");
        Person boss = createPerson("boss", USER, Role.BOSS);
        Optional<String> comment = of("Foo");

        Application applicationForLeave = getDummyApplication(person);
        applicationForLeave.setStatus(ApplicationStatus.TEMPORARY_ALLOWED);
        applicationForLeave.setTwoStageApproval(false);
        when(applicationService.save(applicationForLeave)).thenReturn(applicationForLeave);

        final ApplicationComment applicationComment = commentService.create(applicationForLeave, ApplicationCommentAction.ALLOWED, comment, boss);
        when(applicationComment).thenReturn(new ApplicationComment(person, clock));

        sut.allow(applicationForLeave, boss, comment);

        assertApplicationForLeaveHasChangedStatus(applicationForLeave, ALLOWED, person, boss);
        assertApplicationForLeaveAndCommentAreSaved(applicationForLeave, ApplicationCommentAction.ALLOWED, comment, boss);
        assertNoCalendarSyncIsExecuted();
        assertAllowedNotificationIsSent(applicationForLeave);
    }

    @Test
    void ensureTemporaryAllowedApplicationForLeaveCanBeAllowedByBossEvenWithTwoStageApprovalActive() {

        Person person = new Person("muster", "Muster", "Marlene", "muster@example.org");
        Person boss = createPerson("boss", USER, Role.BOSS);
        Optional<String> comment = of("Foo");

        Application applicationForLeave = getDummyApplication(person);
        applicationForLeave.setStatus(ApplicationStatus.TEMPORARY_ALLOWED);
        applicationForLeave.setTwoStageApproval(true);
        when(applicationService.save(applicationForLeave)).thenReturn(applicationForLeave);

        AbsenceMapping absenceMapping = anyAbsenceMapping();
        when(commentService.create(applicationForLeave, ApplicationCommentAction.ALLOWED, comment, boss)).thenReturn(new ApplicationComment(person, clock));

        sut.allow(applicationForLeave, boss, comment);

        assertApplicationForLeaveHasChangedStatus(applicationForLeave, ALLOWED, person, boss);
        assertApplicationForLeaveAndCommentAreSaved(applicationForLeave, ApplicationCommentAction.ALLOWED, comment, boss);
        assertNoCalendarSyncIsExecuted();
        assertAllowedNotificationIsSent(applicationForLeave);
    }

    @Test
    void ensureIfAllowedApplicationForLeaveIsAllowedAgainNothingHappens() {

        Person person = new Person("muster", "Muster", "Marlene", "muster@example.org");
        Person boss = createPerson("boss", USER, Role.BOSS);
        Optional<String> comment = of("Foo");

        Application applicationForLeave = getDummyApplication(person);
        applicationForLeave.setStatus(ALLOWED);

        sut.allow(applicationForLeave, boss, comment);

        assertThat(applicationForLeave.getStatus()).isEqualTo(ALLOWED);

        verifyNoInteractions(applicationService);
        verifyNoInteractions(commentService);
        verifyNoInteractions(applicationMailService);
        verifyNoInteractions(calendarSyncService);
        verifyNoInteractions(absenceMappingService);
    }

    // ALLOWING - DEPARTMENT HEAD
    @Test
    void ensureThrowsWhenExecutingAllowProcessWithNotPrivilegedUser() {

        Person person = new Person("muster", "Muster", "Marlene", "muster@example.org");
        Person user = new Person("muster", "Muster", "Marlene", "muster@example.org");
        user.setPermissions(Collections.singletonList(USER));

        Optional<String> comment = of("Foo");

        Application applicationForLeave = getDummyApplication(person);
        applicationForLeave.setStatus(WAITING);

        assertThatIllegalStateException()
            .isThrownBy(() -> sut.allow(applicationForLeave, user, comment));
    }

    @Test
    void ensureWaitingApplicationForLeaveCanBeAllowedByDepartmentHead() {

        Person person = new Person("muster", "Muster", "Marlene", "muster@example.org");
        Person departmentHead = createPerson("head", USER, DEPARTMENT_HEAD);
        when(departmentService.isDepartmentHeadOfPerson(eq(departmentHead), eq(person))).thenReturn(true);

        Optional<String> comment = of("Foo");

        final Application applicationForLeave = getDummyApplication(person);
        applicationForLeave.setStatus(WAITING);
        when(applicationService.save(applicationForLeave)).thenReturn(applicationForLeave);

        when(commentService.create(applicationForLeave, ApplicationCommentAction.ALLOWED, comment, departmentHead)).thenReturn(new ApplicationComment(person, clock));

        sut.allow(applicationForLeave, departmentHead, comment);

        assertApplicationForLeaveHasChangedStatus(applicationForLeave, ALLOWED, person, departmentHead);
        assertApplicationForLeaveAndCommentAreSaved(applicationForLeave, ApplicationCommentAction.ALLOWED, comment, departmentHead);
        assertNoCalendarSyncIsExecuted();
        assertAllowedNotificationIsSent(applicationForLeave);
    }

    @Test
    void ensureWaitingApplicationForLeaveCanOnlyBeAllowedTemporaryByDepartmentHeadIfTwoStageApprovalIsActive() {

        final Person person = new Person("muster", "Muster", "Marlene", "muster@example.org");
        final Person departmentHead = createPerson("head", USER, DEPARTMENT_HEAD);
        when(departmentService.isDepartmentHeadOfPerson(eq(departmentHead), eq(person))).thenReturn(true);

        final Optional<String> comment = of("Foo");

        final Application applicationForLeave = getDummyApplication(person);
        applicationForLeave.setStatus(WAITING);
        applicationForLeave.setTwoStageApproval(true);
        when(applicationService.save(applicationForLeave)).thenReturn(applicationForLeave);

        when(commentService.create(applicationForLeave, ApplicationCommentAction.TEMPORARY_ALLOWED, comment, departmentHead)).thenReturn(new ApplicationComment(person, clock));

        sut.allow(applicationForLeave, departmentHead, comment);

        assertApplicationForLeaveHasChangedStatus(applicationForLeave, ApplicationStatus.TEMPORARY_ALLOWED, person, departmentHead);
        assertApplicationForLeaveAndCommentAreSaved(applicationForLeave, ApplicationCommentAction.TEMPORARY_ALLOWED, comment, departmentHead);
        assertNoCalendarSyncOccurs();
        assertTemporaryAllowedNotificationIsSent(applicationForLeave);
    }

    @Test
    void ensureIfTemporaryAllowedApplicationForLeaveIsAllowedByDepartmentHeadWithTwoStageApprovalIsActiveNothingHappens() {

        final Person person = new Person("muster", "Muster", "Marlene", "muster@example.org");
        final Person departmentHead = createPerson("head", USER, DEPARTMENT_HEAD);
        when(departmentService.isDepartmentHeadOfPerson(eq(departmentHead), eq(person))).thenReturn(true);

        final Optional<String> comment = of("Foo");

        final Application applicationForLeave = getDummyApplication(person);
        applicationForLeave.setStatus(ApplicationStatus.TEMPORARY_ALLOWED);
        applicationForLeave.setTwoStageApproval(true);

        sut.allow(applicationForLeave, departmentHead, comment);

        assertThat(applicationForLeave.getStatus()).isEqualTo(ApplicationStatus.TEMPORARY_ALLOWED);

        verifyNoInteractions(applicationService);
        verifyNoInteractions(commentService);
        verifyNoInteractions(applicationMailService);
        verifyNoInteractions(calendarSyncService);
        verifyNoInteractions(absenceMappingService);
    }

    @Test
    void ensureIfTemporaryAllowedApplicationForLeaveIsAllowedByDepartmentHeadWithTwoStageApprovalNotActiveStatusIsChanged() {

        final Person person = new Person("muster", "Muster", "Marlene", "muster@example.org");
        final Person departmentHead = createPerson("head", USER, DEPARTMENT_HEAD);
        when(departmentService.isDepartmentHeadOfPerson(eq(departmentHead), eq(person))).thenReturn(true);

        final Optional<String> comment = of("Foo");

        final Application applicationForLeave = getDummyApplication(person);
        applicationForLeave.setStatus(ApplicationStatus.TEMPORARY_ALLOWED);
        applicationForLeave.setTwoStageApproval(false);
        when(applicationService.save(applicationForLeave)).thenReturn(applicationForLeave);

        when(commentService.create(any(), any(), any(), any())).thenReturn(new ApplicationComment(person, clock));

        sut.allow(applicationForLeave, departmentHead, comment);

        assertApplicationForLeaveHasChangedStatus(applicationForLeave, ALLOWED, person, departmentHead);
        assertApplicationForLeaveAndCommentAreSaved(applicationForLeave, ApplicationCommentAction.ALLOWED, comment, departmentHead);
        assertNoCalendarSyncIsExecuted();
        assertAllowedNotificationIsSent(applicationForLeave);
    }

    // ALLOWING - SECOND STAGE AUTHORITY
    @Test
    void ensureWaitingApplicationForLeaveCanBeAllowedBySecondStageAuthority() {

        final Person person = new Person("muster", "Muster", "Marlene", "muster@example.org");
        final Person secondStage = createPerson("manager", USER, SECOND_STAGE_AUTHORITY);
        when(departmentService.isSecondStageAuthorityOfPerson(eq(secondStage), eq(person))).thenReturn(true);

        final Optional<String> comment = of("Foo");

        final Application applicationForLeave = getDummyApplication(person);
        applicationForLeave.setStatus(WAITING);
        when(applicationService.save(applicationForLeave)).thenReturn(applicationForLeave);

        when(commentService.create(applicationForLeave, ApplicationCommentAction.ALLOWED, comment, secondStage)).thenReturn(new ApplicationComment(person, clock));

        sut.allow(applicationForLeave, secondStage, comment);

        assertApplicationForLeaveHasChangedStatus(applicationForLeave, ALLOWED, person, secondStage);
        assertApplicationForLeaveAndCommentAreSaved(applicationForLeave, ApplicationCommentAction.ALLOWED, comment, secondStage);
        assertNoCalendarSyncIsExecuted();
        assertAllowedNotificationIsSent(applicationForLeave);
    }

    @Test
    void ensureWaitingApplicationForLeaveCanBeAllowedBySecondStageAuthorityIfTwoStageApprovalIsActive() {

        final Person person = new Person("muster", "Muster", "Marlene", "muster@example.org");
        final Person secondStage = createPerson("manager", USER, SECOND_STAGE_AUTHORITY);
        when(departmentService.isSecondStageAuthorityOfPerson(eq(secondStage), eq(person))).thenReturn(true);

        final Optional<String> comment = of("Foo");

        final Application applicationForLeave = getDummyApplication(person);
        applicationForLeave.setStatus(WAITING);
        applicationForLeave.setTwoStageApproval(true);
        when(applicationService.save(applicationForLeave)).thenReturn(applicationForLeave);

        when(commentService.create(applicationForLeave, ApplicationCommentAction.ALLOWED, comment, secondStage)).thenReturn(new ApplicationComment(person, clock));

        sut.allow(applicationForLeave, secondStage, comment);

        assertApplicationForLeaveHasChangedStatus(applicationForLeave, ALLOWED, person, secondStage);
        assertApplicationForLeaveAndCommentAreSaved(applicationForLeave, ApplicationCommentAction.ALLOWED, comment, secondStage);
        assertNoCalendarSyncIsExecuted();
        assertAllowedNotificationIsSent(applicationForLeave);
    }

    @Test
    void ensureTemporaryAllowedApplicationForLeaveCanBeAllowedBySecondStageAuthorityIfTwoStageApprovalIsActive() {

        final Person person = new Person("muster", "Muster", "Marlene", "muster@example.org");
        final Person secondStage = createPerson("manager", USER, SECOND_STAGE_AUTHORITY);
        when(departmentService.isSecondStageAuthorityOfPerson(eq(secondStage), eq(person))).thenReturn(true);

        final Optional<String> comment = of("Foo");

        final Application applicationForLeave = getDummyApplication(person);
        applicationForLeave.setStatus(ApplicationStatus.TEMPORARY_ALLOWED);
        applicationForLeave.setTwoStageApproval(true);
        when(applicationService.save(applicationForLeave)).thenReturn(applicationForLeave);

        final ApplicationComment applicationComment = commentService.create(applicationForLeave, ApplicationCommentAction.ALLOWED, comment, secondStage);
        when(applicationComment).thenReturn(new ApplicationComment(person, clock));

        sut.allow(applicationForLeave, secondStage, comment);

        assertApplicationForLeaveHasChangedStatus(applicationForLeave, ALLOWED, person, secondStage);
        assertApplicationForLeaveAndCommentAreSaved(applicationForLeave, ApplicationCommentAction.ALLOWED, comment, secondStage);
        assertAllowedNotificationIsSent(applicationForLeave);
        verifyNoInteractions(calendarSyncService);
    }

    @Test
    void ensureDepartmentHeadCanBeAllowedBySecondStageAuthority() {

        final Person departmentHead = new Person("muster", "Muster", "Marlene", "muster@example.org");
        departmentHead.setPermissions(asList(USER, DEPARTMENT_HEAD));

        final Person secondStageAuthority = new Person("muster", "Muster", "Marlene", "muster@example.org");
        secondStageAuthority.setPermissions(asList(USER, SECOND_STAGE_AUTHORITY));

        final boolean isSecondStage = departmentService.isSecondStageAuthorityOfPerson(eq(secondStageAuthority), eq(departmentHead));
        when(isSecondStage).thenReturn(true);

        final Application applicationForLeave = getDummyApplication(departmentHead);
        applicationForLeave.setStatus(WAITING);
        when(applicationService.save(applicationForLeave)).thenReturn(applicationForLeave);

        final Optional<String> comment = of("Foo");
        when(commentService.create(applicationForLeave, ApplicationCommentAction.ALLOWED, comment, secondStageAuthority)).thenReturn(new ApplicationComment(departmentHead, clock));

        sut.allow(applicationForLeave, secondStageAuthority, comment);
        assertApplicationForLeaveHasChangedStatus(applicationForLeave, ALLOWED, departmentHead, secondStageAuthority);
        assertApplicationForLeaveAndCommentAreSaved(applicationForLeave, ApplicationCommentAction.ALLOWED, comment, secondStageAuthority);
        assertAllowedNotificationIsSent(applicationForLeave);
        verifyNoInteractions(calendarSyncService);
    }

    @Test
    void ensureSecondStageAuthorityCanNotBeAllowedByDepartmentHead() {

        final Person departmentHead = new Person("muster", "Muster", "Marlene", "muster@example.org");
        departmentHead.setPermissions(asList(USER, DEPARTMENT_HEAD));

        final Person secondStageAuthority = new Person("muster", "Muster", "Marlene", "muster@example.org");
        secondStageAuthority.setPermissions(asList(USER, SECOND_STAGE_AUTHORITY));

        when(departmentService.isDepartmentHeadOfPerson(eq(departmentHead), eq(secondStageAuthority))).thenReturn(true);

        final Optional<String> comment = of("Foo");

        final Application applicationForLeave = getDummyApplication(secondStageAuthority);
        applicationForLeave.setStatus(WAITING);
        applicationForLeave.setTwoStageApproval(true);

        assertThatIllegalStateException().isThrownBy(() -> sut.allow(applicationForLeave, departmentHead, comment));
    }

    @Test
    void ensureSecondStageAuthorityCanNotAllowHimself() {

        final Person secondStageAuthority = new Person("muster", "Muster", "Marlene", "muster@example.org");
        secondStageAuthority.setPermissions(asList(USER, SECOND_STAGE_AUTHORITY));

        when(departmentService.isSecondStageAuthorityOfPerson(eq(secondStageAuthority), eq(secondStageAuthority))).thenReturn(true);

        final Optional<String> comment = of("Foo");

        final Application applicationForLeave = getDummyApplication(secondStageAuthority);
        applicationForLeave.setStatus(WAITING);

        assertThatIllegalStateException().isThrownBy(() -> sut.allow(applicationForLeave, secondStageAuthority, comment));
    }

    @Test
    void ensureDepartmentHeadCanNotAllowHimself() {

        final Person departmentHead = new Person("muster", "Muster", "Marlene", "muster@example.org");
        departmentHead.setPermissions(asList(USER, DEPARTMENT_HEAD));

        when(departmentService.isDepartmentHeadOfPerson(eq(departmentHead), eq(departmentHead))).thenReturn(true);

        final Optional<String> comment = of("Foo");

        final Application applicationForLeave = getDummyApplication(departmentHead);
        applicationForLeave.setStatus(WAITING);

        assertThatIllegalStateException().isThrownBy(() -> sut.allow(applicationForLeave, departmentHead, comment));
    }

    // ALLOWING - HOLIDAY REPLACEMENT NOTIFICATION
    @Test
    void ensureAllowingApplicationForLeaveWithHolidayReplacementSendsNotificationToReplacement() {

        final Person person = new Person("muster", "Muster", "Marlene", "muster@example.org");
        final Person replacement = new Person("muster", "Muster", "Marlene", "muster@example.org");
        final Person boss = createPerson("boss", USER, Role.BOSS);

        final Application applicationForLeave = getDummyApplication(person);
        applicationForLeave.setStatus(WAITING);
        applicationForLeave.setHolidayReplacement(replacement);
        when(applicationService.save(applicationForLeave)).thenReturn(applicationForLeave);

        sut.allow(applicationForLeave, boss, of("Foo"));

        verify(applicationMailService).notifyHolidayReplacementAllow(eq(applicationForLeave));
    }

    @Test
    void ensureAllowingApplicationForLeaveWithoutHolidayReplacementDoesNotSendNotification() {

        final Person person = new Person("muster", "Muster", "Marlene", "muster@example.org");
        final Person boss = createPerson("boss", USER, Role.BOSS);

        final Application applicationForLeave = getDummyApplication(person);
        applicationForLeave.setStatus(WAITING);
        applicationForLeave.setHolidayReplacement(null);
        when(applicationService.save(applicationForLeave)).thenReturn(applicationForLeave);

        sut.allow(applicationForLeave, boss, of("Foo"));

        verify(applicationMailService, never()).notifyHolidayReplacementAllow(any(Application.class));
    }

    @Test
    void ensureTemporaryAllowingApplicationForLeaveWithHolidayReplacementDoesNotSendNotification() {

        final Person person = new Person("muster", "Muster", "Marlene", "muster@example.org");
        final Person replacement = new Person("muster", "Muster", "Marlene", "muster@example.org");
        final Person departmentHead = createPerson("head", USER, DEPARTMENT_HEAD);
        when(departmentService.isDepartmentHeadOfPerson(eq(departmentHead), eq(person))).thenReturn(true);

        final Application applicationForLeave = getDummyApplication(person);
        applicationForLeave.setStatus(WAITING);
        applicationForLeave.setHolidayReplacement(replacement);
        applicationForLeave.setTwoStageApproval(true);

        sut.allow(applicationForLeave, departmentHead, of("Foo"));

        verify(applicationMailService, never()).notifyHolidayReplacementAllow(any(Application.class));
    }

    // REJECT APPLICATION FOR LEAVE ------------------------------------------------------------------------------------
    @Test
    void ensureRejectingApplicationForLeaveChangesStateAndOtherAttributesAndSavesTheApplicationForLeave() {

        final Person person = new Person("muster", "Muster", "Marlene", "muster@example.org");
        final Person boss = new Person("muster", "Muster", "Marlene", "muster@example.org");

        final Optional<String> comment = of("Foo");

        final Application applicationForLeave = getDummyApplication(person);
        applicationForLeave.setStatus(WAITING);
        when(applicationService.save(applicationForLeave)).thenReturn(applicationForLeave);

        sut.reject(applicationForLeave, boss, comment);

        assertThat(applicationForLeave.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
        assertThat(applicationForLeave.getPerson()).isEqualTo(person);
        assertThat(applicationForLeave.getBoss()).isEqualTo(boss);
        assertThat(applicationForLeave.getEditedDate()).isEqualTo(LocalDate.now(UTC));

        verify(applicationService).save(applicationForLeave);
        verify(commentService).create(eq(applicationForLeave), eq(ApplicationCommentAction.REJECTED), eq(comment), eq(boss));
    }

    @Test
    void ensureRejectingApplicationForLeaveDeletesCalendarEvent() {

        final Person person = new Person("muster", "Muster", "Marlene", "muster@example.org");
        final Person boss = new Person("muster", "Muster", "Marlene", "muster@example.org");

        final Optional<String> comment = of("Foo");

        final Application applicationForLeave = getDummyApplication(person);
        applicationForLeave.setStatus(WAITING);
        when(applicationService.save(applicationForLeave)).thenReturn(applicationForLeave);

        final AbsenceMapping absenceMapping = anyAbsenceMapping();
        final Optional<AbsenceMapping> absenceByIdAndType = absenceMappingService.getAbsenceByIdAndType(isNull(), eq(VACATION));
        when(absenceByIdAndType).thenReturn(of(absenceMapping));

        sut.reject(applicationForLeave, boss, comment);

        verify(calendarSyncService).deleteAbsence(absenceMapping.getEventId());
        verify(absenceMappingService).delete(absenceMapping);
    }

    @Test
    void ensureRejectingApplicationForLeaveSendsEmailToPerson() {

        final Person person = new Person("muster", "Muster", "Marlene", "muster@example.org");
        final Person boss = new Person("muster", "Muster", "Marlene", "muster@example.org");

        final Application applicationForLeave = getDummyApplication(person);
        when(applicationService.save(applicationForLeave)).thenReturn(applicationForLeave);

        final Optional<String> optionalComment = of("Foo");
        final ApplicationComment applicationComment = new ApplicationComment(person, clock);

        when(commentService.create(applicationForLeave, ApplicationCommentAction.REJECTED, optionalComment, boss)).thenReturn(applicationComment);

        sut.reject(applicationForLeave, boss, optionalComment);

        verify(applicationMailService).sendRejectedNotification(eq(applicationForLeave), eq(applicationComment));
    }

    // CANCEL APPLICATION FOR LEAVE ------------------------------------------------------------------------------------
    @Test
    void ensureCancelledNotYetAllowedApplicationForLeaveChangesStateAndOtherAttributesAndSendsEmail() {

        final Person person = new Person("muster", "Muster", "Marlene", "muster@example.org");
        final Optional<String> comment = of("Foo");

        final Application applicationForLeave = getDummyApplication(person);
        applicationForLeave.setStatus(WAITING);
        when(applicationService.save(applicationForLeave)).thenReturn(applicationForLeave);

        final ApplicationComment applicationComment = new ApplicationComment(person, clock);
        when(commentService.create(applicationForLeave, ApplicationCommentAction.REVOKED, comment, person)).thenReturn(applicationComment);

        sut.cancel(applicationForLeave, person, comment);
        assertThat(applicationForLeave.getStatus()).isEqualTo(ApplicationStatus.REVOKED);
        assertThat(applicationForLeave.getPerson()).isEqualTo(person);
        assertThat(applicationForLeave.getCanceller()).isEqualTo(person);
        assertThat(applicationForLeave.getCancelDate()).isEqualTo(LocalDate.now(UTC));
        assertThat(applicationForLeave.isFormerlyAllowed()).isFalse();

        verify(applicationMailService).sendRevokedNotifications(applicationForLeave, applicationComment);
    }

    @Test
    void ensureCancellingApplicationForLeaveDeletesCalendarEvent() {

        final Person person = new Person("muster", "Muster", "Marlene", "muster@example.org");
        final Person canceller = new Person("muster", "Muster", "Marlene", "muster@example.org");

        final Optional<String> comment = of("Foo");

        final Application applicationForLeave = getDummyApplication(person);
        applicationForLeave.setStatus(WAITING);
        when(applicationService.save(applicationForLeave)).thenReturn(applicationForLeave);

        final AbsenceMapping absenceMapping = anyAbsenceMapping();
        when(absenceMappingService.getAbsenceByIdAndType(null, VACATION)).thenReturn(of(absenceMapping));

        sut.cancel(applicationForLeave, canceller, comment);

        verify(calendarSyncService).deleteAbsence(anyString());
        verify(absenceMappingService).delete(any(AbsenceMapping.class));
    }

    @Test
    void ensureCancellingAllowedApplicationByOwnerCreatesACancellationRequest() {

        final Person person = new Person("muster", "Muster", "Marlene", "muster@example.org");
        final Optional<String> comment = of("Foo");

        final Application applicationForLeave = getDummyApplication(person);
        applicationForLeave.setStatus(ALLOWED);
        when(applicationService.save(applicationForLeave)).thenReturn(applicationForLeave);

        when(commentService.create(any(Application.class), any(ApplicationCommentAction.class), any(), any(Person.class)))
            .thenReturn(new ApplicationComment(person, clock));

        sut.cancel(applicationForLeave, person, comment);
        assertThat(applicationForLeave.getStatus()).isEqualTo(ApplicationStatus.ALLOWED_CANCELLATION_REQUESTED);

        verify(applicationService).save(applicationForLeave);
        verify(commentService).create(eq(applicationForLeave), eq(CANCEL_REQUESTED), eq(comment), eq(person));
        verify(applicationMailService).sendCancellationRequest(eq(applicationForLeave), any(ApplicationComment.class));
    }

    @Test
    void ensureCancellingAllowedApplicationByOwnerThatIsOfficeCancelsTheApplicationForLeaveDirectly() {

        final Person person = new Person("muster", "Muster", "Marlene", "muster@example.org");
        person.setPermissions(asList(USER, OFFICE));

        final Optional<String> comment = of("Foo");

        final Application applicationForLeave = getDummyApplication(person);
        applicationForLeave.setStatus(ALLOWED);
        when(applicationService.save(applicationForLeave)).thenReturn(applicationForLeave);

        final ApplicationComment applicationComment = new ApplicationComment(person, clock);
        when(commentService.create(applicationForLeave, CANCELLED, comment, person)).thenReturn(applicationComment);

        sut.cancel(applicationForLeave, person, comment);
        assertThat(applicationForLeave.getStatus()).isEqualTo(ApplicationStatus.CANCELLED);
        assertThat(applicationForLeave.getPerson()).isEqualTo(person);
        assertThat(applicationForLeave.getCanceller()).isEqualTo(person);
        assertThat(applicationForLeave.getCancelDate()).isEqualTo(LocalDate.now(UTC));
        assertThat(applicationForLeave.isFormerlyAllowed()).isTrue();

        verify(applicationMailService).sendCancelledByOfficeNotification(applicationForLeave, applicationComment);
    }

    @Test
    void ensureCancellingAllowedApplicationForLeaveOnBehalfForSomeOneChangesStateAndOtherAttributesAndSendsAnEmail() {

        final Person person = new Person("muster", "Muster", "Marlene", "muster@example.org");
        final Person canceller = new Person("muster", "Muster", "Marlene", "muster@example.org");
        canceller.setPermissions(asList(USER, OFFICE));

        final Optional<String> comment = of("Foo");

        final Application applicationForLeave = getDummyApplication(person);
        applicationForLeave.setStatus(ALLOWED);
        when(applicationService.save(applicationForLeave)).thenReturn(applicationForLeave);

        when(commentService.create(any(Application.class), any(ApplicationCommentAction.class), any(), any(Person.class)))
            .thenReturn(new ApplicationComment(person, clock));

        sut.cancel(applicationForLeave, canceller, comment);

        assertThat(applicationForLeave.getStatus()).isEqualTo(ApplicationStatus.CANCELLED);
        assertThat(applicationForLeave.getPerson()).isEqualTo(person);
        assertThat(applicationForLeave.getCanceller()).isEqualTo(canceller);
        assertThat(applicationForLeave.getCancelDate()).isEqualTo(LocalDate.now(UTC));
        assertThat(applicationForLeave.isFormerlyAllowed()).isTrue();

        verify(applicationService).save(applicationForLeave);
        verify(commentService).create(eq(applicationForLeave), eq(CANCELLED), eq(comment), eq(canceller));
        verify(applicationMailService).sendCancelledByOfficeNotification(eq(applicationForLeave), any(ApplicationComment.class));
    }

    @Test
    void cancellingNotYetAllowedApplicationAsOffice() {

        final Person person = new Person("muster", "Muster", "Marlene", "muster@example.org");
        final Person canceller = new Person("muster", "Muster", "Marlene", "muster@example.org");
        canceller.setPermissions(asList(USER, OFFICE));

        final Optional<String> comment = of("Foo");

        final Application applicationForLeave = getDummyApplication(person);
        applicationForLeave.setStatus(WAITING);
        when(applicationService.save(applicationForLeave)).thenReturn(applicationForLeave);

        when(commentService.create(any(Application.class), any(ApplicationCommentAction.class), any(), any(Person.class)))
            .thenReturn(new ApplicationComment(person, clock));

        sut.cancel(applicationForLeave, canceller, comment);
        assertThat(applicationForLeave.getStatus()).isEqualTo(ApplicationStatus.REVOKED);
        assertThat(applicationForLeave.getPerson()).isEqualTo(person);
        assertThat(applicationForLeave.getCanceller()).isEqualTo(canceller);
        assertThat(applicationForLeave.getCancelDate()).isEqualTo(LocalDate.now(UTC));
        assertThat(applicationForLeave.isFormerlyAllowed()).isFalse();

        verify(applicationService).save(applicationForLeave);
        verify(commentService).create(eq(applicationForLeave), eq(ApplicationCommentAction.REVOKED), eq(comment), eq(canceller));
        verify(applicationMailService).sendRevokedNotifications(eq(applicationForLeave), any(ApplicationComment.class));
    }

    @Test
    void cancellingNotYetAllowedApplicationAsUser() {

        final Person person = createPerson("muster");
        final Person canceller = createPerson("canceller");

        final Optional<String> comment = of("Foo");

        final Application applicationForLeave = getDummyApplication(person);
        applicationForLeave.setStatus(WAITING);
        when(applicationService.save(applicationForLeave)).thenReturn(applicationForLeave);

        final ApplicationComment applicationComment = new ApplicationComment(person, clock);
        when(commentService.create(any(Application.class), any(ApplicationCommentAction.class), any(), any(Person.class)))
            .thenReturn(applicationComment);

        sut.cancel(applicationForLeave, canceller, comment);
        assertThat(applicationForLeave.getStatus()).isEqualTo(ApplicationStatus.REVOKED);
        assertThat(applicationForLeave.getPerson()).isEqualTo(person);
        assertThat(applicationForLeave.getCanceller()).isEqualTo(canceller);
        assertThat(applicationForLeave.getCancelDate()).isEqualTo(LocalDate.now(UTC));
        assertThat(applicationForLeave.isFormerlyAllowed()).isFalse();

        verify(applicationService).save(applicationForLeave);
        verify(commentService).create(eq(applicationForLeave), eq(ApplicationCommentAction.REVOKED), eq(comment), eq(canceller));
        verify(applicationMailService).sendRevokedNotifications(applicationForLeave, applicationComment);
    }

    @Test
    void ensureCancellingApplicationForLeaveUpdatesRemainingVacationDaysWithTheYearOfTheStartDateAsStartYear() {

        final Person person = new Person("muster", "Muster", "Marlene", "muster@example.org");
        final Person canceller = new Person("muster", "Muster", "Marlene", "muster@example.org");
        canceller.setPermissions(asList(USER, OFFICE));

        final Optional<String> comment = of("Foo");

        final Application applicationForLeave = new Application();
        applicationForLeave.setPerson(person);
        applicationForLeave.setStatus(ALLOWED);
        applicationForLeave.setStartDate(LocalDate.of(2014, 12, 24));
        applicationForLeave.setEndDate(LocalDate.of(2015, 1, 7));
        applicationForLeave.setDayLength(DayLength.FULL);
        when(applicationService.save(applicationForLeave)).thenReturn(applicationForLeave);

        sut.cancel(applicationForLeave, canceller, comment);

        verify(accountInteractionService).updateRemainingVacationDays(2014, person);
    }

    // decline cancellation request -------------------------------------------------------------------------------------
    @Test
    void declineCancellationRequest() {

        final Person person = new Person("muster", "Muster", "Marlene", "muster@example.org");
        final Person canceller = new Person("muster", "Muster", "Marlene", "muster@example.org");

        final Application applicationForLeave = new Application();
        applicationForLeave.setPerson(person);
        applicationForLeave.setStatus(ALLOWED_CANCELLATION_REQUESTED);
        applicationForLeave.setStartDate(LocalDate.of(2014, 12, 24));
        applicationForLeave.setEndDate(LocalDate.of(2015, 1, 7));
        applicationForLeave.setDayLength(DayLength.FULL);
        when(applicationService.save(applicationForLeave)).thenReturn(applicationForLeave);

        final Optional<String> comment = of("Anfrage kann nicht storniert werden!");
        final ApplicationComment applicationComment = new ApplicationComment(canceller, clock);
        when(commentService.create(applicationForLeave, CANCEL_REQUESTED_DECLINED, comment, canceller)).thenReturn(applicationComment);

        final Application application = sut.declineCancellationRequest(applicationForLeave, canceller, comment);
        assertThat(application.getStatus()).isEqualTo(ALLOWED);

        verify(applicationMailService).sendDeclinedCancellationRequestApplicationNotification(application, applicationComment);
    }

    @Test
    void declineCancellationRequestWrongStatus() {

        final Person person = new Person("muster", "Muster", "Marlene", "muster@example.org");
        final Person canceller = new Person("muster", "Muster", "Marlene", "muster@example.org");

        final Optional<String> comment = of("Anfrage kann nicht storniert werden!");

        final Application applicationForLeave = new Application();
        applicationForLeave.setPerson(person);
        applicationForLeave.setStatus(ALLOWED);
        applicationForLeave.setStartDate(LocalDate.of(2014, 12, 24));
        applicationForLeave.setEndDate(LocalDate.of(2015, 1, 7));
        applicationForLeave.setDayLength(DayLength.FULL);

        assertThatThrownBy(() -> sut.declineCancellationRequest(applicationForLeave, canceller, comment))
            .isInstanceOf(DeclineCancellationRequestedApplicationForLeaveNotAllowedException.class);

        verifyNoMoreInteractions(commentService, applicationMailService, applicationService);
    }

    // CREATE APPLICATION FOR LEAVE FROM CONVERTED SICK NOTE -----------------------------------------------------------
    @Test
    void ensureCreatedApplicationForLeaveFromConvertedSickNoteIsAllowedDirectly() {

        final Person person = new Person("muster", "Muster", "Marlene", "muster@example.org");
        final Person creator = new Person("muster", "Muster", "Marlene", "muster@example.org");

        final Application applicationForLeave = new Application();
        applicationForLeave.setPerson(person);
        applicationForLeave.setStatus(null);
        applicationForLeave.setStartDate(LocalDate.of(2014, 12, 24));
        applicationForLeave.setEndDate(LocalDate.of(2015, 1, 7));
        applicationForLeave.setDayLength(DayLength.FULL);
        when(applicationService.save(applicationForLeave)).thenReturn(applicationForLeave);

        sut.createFromConvertedSickNote(applicationForLeave, creator);

        verify(applicationService).save(applicationForLeave);
        verify(commentService).create(eq(applicationForLeave), eq(CONVERTED), eq(Optional.empty()), eq(creator));
        verify(applicationMailService).sendSickNoteConvertedToVacationNotification(eq(applicationForLeave));

        assertThat(applicationForLeave.getStatus()).isNotNull();
        assertThat(applicationForLeave.getApplier()).isNotNull();
        assertThat(applicationForLeave.getPerson()).isNotNull();

        assertThat(applicationForLeave.getStatus()).isEqualTo(ALLOWED);
        assertThat(applicationForLeave.getApplier()).isEqualTo(creator);
        assertThat(applicationForLeave.getPerson()).isEqualTo(person);
    }

    // REMIND ----------------------------------------------------------------------------------------------------------
    @Test
    void ensureThrowsIfAlreadySentRemindToday() {

        final Application applicationForLeave = mock(Application.class);
        when(applicationForLeave.getRemindDate()).thenReturn(LocalDate.now(UTC));

        assertThatThrownBy(() -> sut.remind(applicationForLeave)).isInstanceOf(RemindAlreadySentException.class);

        verify(applicationForLeave, never()).setRemindDate(any(LocalDate.class));
        verifyNoInteractions(applicationService);
        verifyNoInteractions(applicationMailService);
    }

    @Test
    void ensureThrowsIfTryingToRemindTooEarly() {

        final Application applicationForLeave = mock(Application.class);
        when(applicationForLeave.getApplicationDate()).thenReturn(LocalDate.now(UTC));
        when(applicationForLeave.getRemindDate()).thenReturn(null);

        assertThatThrownBy(() -> sut.remind(applicationForLeave))
            .isInstanceOf(ImpatientAboutApplicationForLeaveProcessException.class);

        verify(applicationForLeave, never()).setRemindDate(any(LocalDate.class));
        verifyNoInteractions(applicationService);
        verifyNoInteractions(applicationMailService);
    }

    @Test
    void ensureUpdatesRemindDateAndSendsMail() throws RemindAlreadySentException,
        ImpatientAboutApplicationForLeaveProcessException {

        final Person person = new Person("muster", "Muster", "Marlene", "muster@example.org");
        final Application applicationForLeave = createApplication(person, createVacationType(HOLIDAY));
        applicationForLeave.setApplicationDate(LocalDate.now(UTC).minusDays(3));
        applicationForLeave.setRemindDate(LocalDate.now(UTC).minusDays(1));

        sut.remind(applicationForLeave);

        assertThat(applicationForLeave.getRemindDate()).isNotNull();
        assertThat(applicationForLeave.getRemindDate()).isEqualTo(LocalDate.now(UTC));

        verify(applicationService).save(eq(applicationForLeave));
        verify(applicationMailService).sendRemindBossNotification(eq(applicationForLeave));
    }

    // REFER -----------------------------------------------------------------------------------------------------------
    @Test
    void ensureReferMailIsSent() {

        final Person recipient = new Person("muster", "Muster", "Marlene", "muster@example.org");
        final Person sender = new Person("muster", "Muster", "Marlene", "muster@example.org");

        final Application applicationForLeave = mock(Application.class);
        sut.refer(applicationForLeave, recipient, sender);

        verify(applicationMailService).sendReferApplicationNotification(applicationForLeave, recipient, sender);
    }

    @Test
    void ensureReferCommentWasAdded() {

        final Person recipient = new Person("muster", "Muster", "Marlene", "muster@example.org");
        final Person sender = new Person("muster", "Muster", "Marlene", "muster@example.org");

        final Application applicationForLeave = mock(Application.class);
        sut.refer(applicationForLeave, recipient, sender);

        verify(commentService).create(applicationForLeave, REFERRED, Optional.of(recipient.getNiceName()), sender);
    }

    // GET -----------------------------------------------------------------------------------------------------------
    @Test
    void getApplicationForLeave() {

        final Integer applicationId = 1;

        final Person person = new Person("muster", "Muster", "Marlene", "muster@example.org");
        final Application application = createApplication(person, createVacationType(HOLIDAY));
        application.setId(applicationId);

        when(applicationService.getApplicationById(applicationId)).thenReturn(Optional.of(application));

        final Optional<Application> getApplication = sut.get(applicationId);
        assertThat(getApplication).hasValue(application);
    }

    // EDIT -----------------------------------------------------------------------------------------------------------
    @Test
    void editApplicationForLeave() {

        final Integer applicationId = 1;

        final Person person = new Person("muster", "Muster", "Marlene", "muster@example.org");
        final Application application = createApplication(person, createVacationType(HOLIDAY));
        application.setStatus(WAITING);
        application.setId(applicationId);
        when(applicationService.save(application)).thenReturn(application);

        final Optional<String> comment = of("Comment");

        final Application editedApplication = sut.edit(application, application, person, comment);
        assertThat(editedApplication.getStatus()).isEqualTo(WAITING);

        verify(commentService).create(editedApplication, EDITED, comment, person);
        verify(applicationMailService).sendEditedApplicationNotification(editedApplication, person);
        verifyNoMoreInteractions(applicationMailService);
    }

    @Test
    void editApplicationForLeaveHolidayReplacementAdded() {

        final Integer applicationId = 1;

        final Person person = new Person("muster", "Muster", "Marlene", "muster@example.org");

        final Person newHolidayReplacement = new Person("muster", "Muster", "Marlene", "muster@example.org");
        newHolidayReplacement.setId(1);

        final Application newApplication = createApplication(person, createVacationType(HOLIDAY));
        newApplication.setStatus(WAITING);
        newApplication.setId(applicationId);
        newApplication.setHolidayReplacement(newHolidayReplacement);
        when(applicationService.save(newApplication)).thenReturn(newApplication);

        final Application oldApplication = createApplication(person, createVacationType(HOLIDAY));

        final Optional<String> comment = of("Comment");

        final Application editedApplication = sut.edit(oldApplication, newApplication, person, comment);
        assertThat(editedApplication.getStatus()).isEqualTo(WAITING);

        verify(commentService).create(editedApplication, EDITED, comment, person);
        verify(applicationMailService).notifyHolidayReplacementForApply(newApplication);
        verify(applicationMailService).sendEditedApplicationNotification(editedApplication, person);
        verifyNoMoreInteractions(applicationMailService);
    }

    @Test
    void editApplicationForLeaveHolidayReplacementChanged() {

        final Integer applicationId = 1;

        final Person person = new Person("muster", "Muster", "Marlene", "muster@example.org");

        final Person newHolidayReplacement = new Person("muster", "Muster", "Marlene", "muster@example.org");
        newHolidayReplacement.setId(1);

        final Application newApplication = createApplication(person, createVacationType(HOLIDAY));
        newApplication.setStatus(WAITING);
        newApplication.setId(applicationId);
        newApplication.setHolidayReplacement(newHolidayReplacement);
        when(applicationService.save(newApplication)).thenReturn(newApplication);

        final Person oldHolidayReplacement = new Person("muster", "Muster", "Marlene", "muster@example.org");
        oldHolidayReplacement.setId(2);
        final Application oldApplication = createApplication(person, createVacationType(HOLIDAY));
        oldApplication.setHolidayReplacement(oldHolidayReplacement);

        final Optional<String> comment = of("Comment");

        final Application editedApplication = sut.edit(oldApplication, newApplication, person, comment);
        assertThat(editedApplication.getStatus()).isEqualTo(WAITING);

        verify(commentService).create(editedApplication, EDITED, comment, person);
        verify(applicationMailService).notifyHolidayReplacementForApply(newApplication);
        verify(applicationMailService).notifyHolidayReplacementAboutCancellation(oldApplication);
        verify(applicationMailService).sendEditedApplicationNotification(editedApplication, person);
        verifyNoMoreInteractions(applicationMailService);
    }

    @Test
    void editApplicationForLeaveHolidayReplacementRemoved() {

        final Integer applicationId = 1;

        final Person person = new Person("muster", "Muster", "Marlene", "muster@example.org");

        final Application newApplication = createApplication(person, createVacationType(HOLIDAY));
        newApplication.setStatus(WAITING);
        newApplication.setId(applicationId);
        when(applicationService.save(newApplication)).thenReturn(newApplication);

        final Person oldHolidayReplacement = new Person("muster", "Muster", "Marlene", "muster@example.org");
        oldHolidayReplacement.setId(2);
        final Application oldApplication = createApplication(person, createVacationType(HOLIDAY));
        oldApplication.setHolidayReplacement(oldHolidayReplacement);

        final Optional<String> comment = of("Comment");

        final Application editedApplication = sut.edit(oldApplication, newApplication, person, comment);
        assertThat(editedApplication.getStatus()).isEqualTo(WAITING);

        verify(commentService).create(editedApplication, EDITED, comment, person);
        verify(applicationMailService).notifyHolidayReplacementAboutCancellation(oldApplication);
        verify(applicationMailService).sendEditedApplicationNotification(editedApplication, person);
        verifyNoMoreInteractions(applicationMailService);
    }

    @Test
    void editApplicationForLeaveHolidayRelevantEntriesChangedFromTo() {

        final Integer applicationId = 1;

        final Person person = new Person("muster", "Muster", "Marlene", "muster@example.org");

        final Person holidayReplacement = new Person("muster", "Muster", "Marlene", "muster@example.org");
        holidayReplacement.setId(2);

        final Application newApplication = createApplication(person, createVacationType(HOLIDAY));
        newApplication.setStatus(WAITING);
        newApplication.setId(applicationId);
        newApplication.setStartDate(LocalDate.of(2020, 10, 3));
        newApplication.setEndDate(LocalDate.of(2020, 10, 3));
        newApplication.setHolidayReplacement(holidayReplacement);
        when(applicationService.save(newApplication)).thenReturn(newApplication);

        final Application oldApplication = createApplication(person, createVacationType(HOLIDAY));
        oldApplication.setHolidayReplacement(holidayReplacement);
        oldApplication.setStartDate(LocalDate.of(2020, 10, 4));
        oldApplication.setEndDate(LocalDate.of(2020, 10, 4));

        final Optional<String> comment = of("Comment");

        final Application editedApplication = sut.edit(oldApplication, newApplication, person, comment);
        assertThat(editedApplication.getStatus()).isEqualTo(WAITING);

        verify(commentService).create(editedApplication, EDITED, comment, person);
        verify(applicationMailService).notifyHolidayReplacementAboutEdit(newApplication);
        verify(applicationMailService).sendEditedApplicationNotification(editedApplication, person);
        verifyNoMoreInteractions(applicationMailService);
    }

    @Test
    void editApplicationForLeaveHolidayRelevantEntriesChangedDayLeng() {

        final Integer applicationId = 1;

        final Person person = new Person("muster", "Muster", "Marlene", "muster@example.org");

        final Person holidayReplacement = new Person("muster", "Muster", "Marlene", "muster@example.org");
        holidayReplacement.setId(2);

        final Application newApplication = createApplication(person, createVacationType(HOLIDAY));
        newApplication.setStatus(WAITING);
        newApplication.setId(applicationId);
        newApplication.setDayLength(DayLength.FULL);
        newApplication.setHolidayReplacement(holidayReplacement);
        when(applicationService.save(newApplication)).thenReturn(newApplication);

        final Application oldApplication = createApplication(person, createVacationType(HOLIDAY));
        oldApplication.setHolidayReplacement(holidayReplacement);
        oldApplication.setDayLength(DayLength.NOON);

        final Optional<String> comment = of("Comment");

        final Application editedApplication = sut.edit(oldApplication, newApplication, person, comment);
        assertThat(editedApplication.getStatus()).isEqualTo(WAITING);

        verify(commentService).create(editedApplication, EDITED, comment, person);
        verify(applicationMailService).notifyHolidayReplacementAboutEdit(newApplication);
        verify(applicationMailService).sendEditedApplicationNotification(editedApplication, person);
        verifyNoMoreInteractions(applicationMailService);
    }

    @Test
    void editApplicationForLeaveHasWrongStatus() {

        final Person person = new Person("muster", "Muster", "Marlene", "muster@example.org");
        final Application application = createApplication(person, createVacationType(HOLIDAY));
        application.setStatus(ALLOWED);

        final Optional<String> comment = of("Comment");

        assertThatThrownBy(() -> sut.edit(application, application, person, comment))
            .isInstanceOf(EditApplicationForLeaveNotAllowedException.class);

        verifyNoInteractions(applicationMailService);
        verifyNoInteractions(commentService);
    }


    private void assertApplicationForLeaveHasChangedStatus(Application applicationForLeave, ApplicationStatus status,
                                                           Person person, Person privilegedUser) {

        assertThat(applicationForLeave.getStatus()).isEqualTo(status);
        assertThat(applicationForLeave.getPerson()).isEqualTo(person);
        assertThat(applicationForLeave.getBoss()).isEqualTo(privilegedUser);
        assertThat(applicationForLeave.getEditedDate()).isEqualTo(LocalDate.now(UTC));
    }


    private void assertApplicationForLeaveAndCommentAreSaved(Application applicationForLeave, ApplicationCommentAction action,
                                                             Optional<String> optionalComment, Person privilegedUser) {
        verify(applicationService).save(applicationForLeave);
        verify(commentService).create(eq(applicationForLeave), eq(action), eq(optionalComment), eq(privilegedUser));
    }

    private void assertNoCalendarSyncIsExecuted() {
        verifyNoInteractions(calendarSyncService);
        verifyNoInteractions(absenceMappingService);
    }

    private void assertAllowedNotificationIsSent(Application applicationForLeave) {
        verify(applicationMailService).sendAllowedNotification(eq(applicationForLeave), any(ApplicationComment.class));
        verify(applicationMailService, never()).sendTemporaryAllowedNotification(any(Application.class), any(ApplicationComment.class));
    }

    private void assertNoCalendarSyncOccurs() {
        verifyNoInteractions(calendarSyncService);
        verifyNoInteractions(absenceMappingService);
    }

    private void assertTemporaryAllowedNotificationIsSent(Application applicationForLeave) {
        verify(applicationMailService).sendTemporaryAllowedNotification(eq(applicationForLeave), any(ApplicationComment.class));
        verify(applicationMailService, never()).sendAllowedNotification(any(Application.class), any(ApplicationComment.class));
    }

    private Application getDummyApplication(Person person) {
        final Application applicationForLeave = new Application();
        applicationForLeave.setPerson(person);
        applicationForLeave.setStartDate(LocalDate.of(2013, 2, 1));
        applicationForLeave.setEndDate(LocalDate.of(2013, 2, 5));
        applicationForLeave.setDayLength(DayLength.FULL);
        applicationForLeave.setHolidayReplacement(new Person("muster", "Muster", "Marlene", "muster@example.org"));

        return applicationForLeave;
    }
}
