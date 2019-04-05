package org.synyx.urlaubsverwaltung.sicknote.api;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.synyx.urlaubsverwaltung.person.Person;
import org.synyx.urlaubsverwaltung.person.PersonService;
import org.synyx.urlaubsverwaltung.sicknote.SickNote;
import org.synyx.urlaubsverwaltung.sicknote.SickNoteService;
import org.synyx.urlaubsverwaltung.api.ResponseWrapper;
import org.synyx.urlaubsverwaltung.api.RestApiDateFormat;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;


@Api("Sick Notes: Get all sick notes for a certain period")
@RestController("restApiSickNoteController")
@RequestMapping("/api")
public class SickNoteController {

    private final SickNoteService sickNoteService;
    private final PersonService personService;

    @Autowired
    SickNoteController(SickNoteService sickNoteService, PersonService personService) {

        this.sickNoteService = sickNoteService;
        this.personService = personService;
    }

    @ApiOperation(
        value = "Get all sick notes for a certain period", notes = "Get all sick notes for a certain period. "
        + "If a person is specified, only the sick notes of this person are fetched. "
        + "Information only reachable for users with role office."
    )
    @GetMapping("/sicknotes")
    public ResponseWrapper<SickNoteListResponse> sickNotes(
        @ApiParam(value = "Start date with pattern yyyy-MM-dd", defaultValue = RestApiDateFormat.EXAMPLE_FIRST_DAY_OF_YEAR)
        @RequestParam(value = "from")
            String from,
        @ApiParam(value = "End date with pattern yyyy-MM-dd", defaultValue = RestApiDateFormat.EXAMPLE_LAST_DAY_OF_YEAR)
        @RequestParam(value = "to")
            String to,
        @ApiParam(value = "ID of the person")
        @RequestParam(value = "person", required = false)
            Integer personId) {

        LocalDate startDate;
        LocalDate endDate;
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(RestApiDateFormat.DATE_PATTERN);
            startDate = LocalDate.parse(from, formatter);
            endDate = LocalDate.parse(to, formatter);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(exception.getMessage());
        }

        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Parameter 'from' must be before or equals to 'to' parameter");
        }

        Optional<Person> optionalPerson = personId == null ? Optional.empty() : personService.getPersonByID(personId);

        List<SickNote> sickNotes;

        if (optionalPerson.isPresent()) {
            sickNotes = sickNoteService.getByPersonAndPeriod(optionalPerson.get(), startDate, endDate);
        } else {
            sickNotes = sickNoteService.getByPeriod(startDate, endDate);
        }

        List<AbsenceResponse> sickNoteResponses = sickNotes.stream()
            .filter(SickNote::isActive)
            .map(AbsenceResponse::new)
            .collect(Collectors.toList());

        return new ResponseWrapper<>(new SickNoteListResponse(sickNoteResponses));
    }
}
