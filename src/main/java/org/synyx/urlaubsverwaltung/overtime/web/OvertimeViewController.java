package org.synyx.urlaubsverwaltung.overtime.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.DataBinder;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.synyx.urlaubsverwaltung.department.DepartmentService;
import org.synyx.urlaubsverwaltung.overtime.Overtime;
import org.synyx.urlaubsverwaltung.overtime.OvertimeCommentAction;
import org.synyx.urlaubsverwaltung.overtime.OvertimeService;
import org.synyx.urlaubsverwaltung.person.Person;
import org.synyx.urlaubsverwaltung.person.PersonService;
import org.synyx.urlaubsverwaltung.person.UnknownPersonException;
import org.synyx.urlaubsverwaltung.person.web.PersonPropertyEditor;
import org.synyx.urlaubsverwaltung.web.DecimalNumberPropertyEditor;
import org.synyx.urlaubsverwaltung.web.LocalDatePropertyEditor;

import javax.validation.Valid;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Year;
import java.util.Locale;
import java.util.Optional;

import static org.synyx.urlaubsverwaltung.person.Role.OFFICE;


/**
 * Manage overtime of persons.
 */
@Controller
@RequestMapping("/web")
public class OvertimeViewController {

    private static final String PERSON_ATTRIBUTE = "person";
    private static final String SIGNED_IN_USER = "signedInUser";
    private static final String OVERTIME = "overtime";
    private static final String OVERTIME_OVERTIME_FORM = "overtime/overtime_form";

    private final OvertimeService overtimeService;
    private final PersonService personService;
    private final OvertimeFormValidator validator;
    private final DepartmentService departmentService;
    private final Clock clock;

    @Autowired
    public OvertimeViewController(OvertimeService overtimeService, PersonService personService,
                                  OvertimeFormValidator validator, DepartmentService departmentService, Clock clock) {
        this.overtimeService = overtimeService;
        this.personService = personService;
        this.validator = validator;
        this.departmentService = departmentService;
        this.clock = clock;
    }

    @InitBinder
    public void initBinder(DataBinder binder, Locale locale) {

        binder.registerCustomEditor(LocalDate.class, new LocalDatePropertyEditor());
        binder.registerCustomEditor(BigDecimal.class, new DecimalNumberPropertyEditor(locale));
        binder.registerCustomEditor(Person.class, new PersonPropertyEditor(personService));
    }

    @GetMapping("/overtime")
    public String showPersonalOvertime() {

        final Person signedInUser = personService.getSignedInUser();
        return "redirect:/web/overtime?person=" + signedInUser.getId();
    }

    @GetMapping(value = "/overtime", params = PERSON_ATTRIBUTE)
    public String showOvertime(
        @RequestParam(value = PERSON_ATTRIBUTE) Integer personId,
        @RequestParam(value = "year", required = false) Integer requestedYear, Model model)
        throws UnknownPersonException {

        final int year = requestedYear == null ? Year.now(clock).getValue() : requestedYear;
        final Person person = personService.getPersonByID(personId).orElseThrow(() -> new UnknownPersonException(personId));
        final Person signedInUser = personService.getSignedInUser();

        if (!departmentService.isSignedInUserAllowedToAccessPersonData(signedInUser, person)) {
            throw new AccessDeniedException(String.format(
                "User '%s' has not the correct permissions to see overtime records of user '%s'",
                signedInUser.getId(), person.getId()));
        }

        model.addAttribute("year", year);
        model.addAttribute(PERSON_ATTRIBUTE, person);
        model.addAttribute(SIGNED_IN_USER, signedInUser);

        final OvertimeListDto overtimeListDto = OvertimeListMapper.mapToDto(
            overtimeService.getOvertimeRecordsForPersonAndYear(person, year),
            overtimeService.getTotalOvertimeForPersonAndYear(person, year),
            overtimeService.getLeftOvertimeForPerson(person));

        model.addAttribute("records", overtimeListDto.getRecords());
        model.addAttribute("overtimeTotal", overtimeListDto.getOvertimeTotal());
        model.addAttribute("overtimeLeft", overtimeListDto.getOvertimeLeft());

        return "overtime/overtime_list";
    }


    @GetMapping("/overtime/{id}")
    public String showOvertimeDetails(@PathVariable("id") Integer id, Model model) throws UnknownOvertimeException {

        final Overtime overtime = overtimeService.getOvertimeById(id).orElseThrow(() -> new UnknownOvertimeException(id));
        final Person person = overtime.getPerson();
        final Person signedInUser = personService.getSignedInUser();

        if (!departmentService.isSignedInUserAllowedToAccessPersonData(signedInUser, person)) {
            throw new AccessDeniedException(String.format(
                "User '%s' has not the correct permissions to see overtime records of user '%s'",
                signedInUser.getId(), person.getId()));
        }

        model.addAttribute(SIGNED_IN_USER, signedInUser);

        final OvertimeDetailsDto overtimeDetailsDto = OvertimeDetailsMapper.mapToDto(
            overtime,
            overtimeService.getCommentsForOvertime(overtime),
            overtimeService.getTotalOvertimeForPersonAndYear(person, overtime.getEndDate().getYear()),
            overtimeService.getLeftOvertimeForPerson(person));

        model.addAttribute("record", overtimeDetailsDto.getRecord());
        model.addAttribute("comments", overtimeDetailsDto.getComments());
        model.addAttribute("overtimeTotal", overtimeDetailsDto.getOvertimeTotal());
        model.addAttribute("overtimeLeft", overtimeDetailsDto.getOvertimeLeft());

        return "overtime/overtime_details";
    }

    @GetMapping("/overtime/new")
    public String recordOvertime(
        @RequestParam(value = PERSON_ATTRIBUTE, required = false) Integer personId, Model model)
        throws UnknownPersonException {

        final Person signedInUser = personService.getSignedInUser();
        final Person person;

        if (personId != null) {
            person = personService.getPersonByID(personId).orElseThrow(() -> new UnknownPersonException(personId));
        } else {
            person = signedInUser;
        }

        if (!signedInUser.equals(person) && !signedInUser.hasRole(OFFICE)) {
            throw new AccessDeniedException(String.format(
                "User '%s' has not the correct permissions to record overtime for user '%s'",
                signedInUser.getId(), person.getId()));
        }

        model.addAttribute(OVERTIME, new OvertimeForm(person));
        model.addAttribute(PERSON_ATTRIBUTE, person);
        model.addAttribute(SIGNED_IN_USER, signedInUser);

        return OVERTIME_OVERTIME_FORM;
    }


    @PostMapping("/overtime")
    public String recordOvertime(@Valid @ModelAttribute(OVERTIME) OvertimeForm overtimeForm, Errors errors, Model model,
                                 RedirectAttributes redirectAttributes) {

        final Person signedInUser = personService.getSignedInUser();
        final Person person = overtimeForm.getPerson();

        if (!signedInUser.equals(person) && !signedInUser.hasRole(OFFICE)) {
            throw new AccessDeniedException(String.format(
                "User '%s' has not the correct permissions to record overtime for user '%s'",
                signedInUser.getId(), person.getId()));
        }

        validator.validate(overtimeForm, errors);

        if (errors.hasErrors()) {
            model.addAttribute(OVERTIME, overtimeForm);
            model.addAttribute(PERSON_ATTRIBUTE, person);
            model.addAttribute(SIGNED_IN_USER, signedInUser);
            return OVERTIME_OVERTIME_FORM;
        }

        final Overtime overtime = overtimeForm.generateOvertime();
        final Optional<String> overtimeFormComment = Optional.ofNullable(overtimeForm.getComment());
        final Overtime recordedOvertime = overtimeService.record(overtime, overtimeFormComment, signedInUser);

        redirectAttributes.addFlashAttribute("overtimeRecord", OvertimeCommentAction.CREATED.name());
        return "redirect:/web/overtime/" + recordedOvertime.getId();
    }

    @GetMapping("/overtime/{id}/edit")
    public String editOvertime(@PathVariable("id") Integer id, Model model) throws UnknownOvertimeException {

        final Overtime overtime = overtimeService.getOvertimeById(id).orElseThrow(() -> new UnknownOvertimeException(id));
        final Person signedInUser = personService.getSignedInUser();
        final Person person = overtime.getPerson();

        if (!signedInUser.equals(person) && !signedInUser.hasRole(OFFICE)) {
            throw new AccessDeniedException(String.format(
                "User '%s' has not the correct permissions to edit overtime record of user '%s'",
                signedInUser.getId(), person.getId()));
        }

        model.addAttribute(OVERTIME, new OvertimeForm(overtime));
        model.addAttribute(PERSON_ATTRIBUTE, person);
        model.addAttribute(SIGNED_IN_USER, signedInUser);

        return OVERTIME_OVERTIME_FORM;
    }

    @PostMapping("/overtime/{id}")
    public String updateOvertime(@PathVariable("id") Integer id,
                                 @ModelAttribute(OVERTIME) OvertimeForm overtimeForm, Errors errors, Model model,
                                 RedirectAttributes redirectAttributes) throws UnknownOvertimeException {

        final Overtime overtime = overtimeService.getOvertimeById(id).orElseThrow(() -> new UnknownOvertimeException(id));
        final Person signedInUser = personService.getSignedInUser();
        final Person person = overtime.getPerson();

        if (!signedInUser.equals(person) && !signedInUser.hasRole(OFFICE)) {
            throw new AccessDeniedException(String.format(
                "User '%s' has not the correct permissions to edit overtime record of user '%s'",
                signedInUser.getId(), person.getId()));
        }

        validator.validate(overtimeForm, errors);

        if (errors.hasErrors()) {
            model.addAttribute(OVERTIME, overtimeForm);
            model.addAttribute(SIGNED_IN_USER, signedInUser);
            return OVERTIME_OVERTIME_FORM;
        }

        overtimeForm.updateOvertime(overtime);
        overtimeService.record(overtime, Optional.ofNullable(overtimeForm.getComment()), signedInUser);

        redirectAttributes.addFlashAttribute("overtimeRecord", OvertimeCommentAction.EDITED.name());
        return "redirect:/web/overtime/" + id;
    }
}
