package org.synyx.urlaubsverwaltung.sicknote;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.synyx.urlaubsverwaltung.person.Person;
import org.synyx.urlaubsverwaltung.settings.Settings;
import org.synyx.urlaubsverwaltung.settings.SettingsService;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Implementation for {@link org.synyx.urlaubsverwaltung.sicknote.SickNoteService}.
 */
@Service
class SickNoteServiceImpl implements SickNoteService {

    private final SickNoteRepository sickNoteRepository;
    private final SettingsService settingsService;
    private final Clock clock;

    @Autowired
    public SickNoteServiceImpl(SickNoteRepository sickNoteRepository, SettingsService settingsService, Clock clock) {
        this.sickNoteRepository = sickNoteRepository;
        this.settingsService = settingsService;
        this.clock = clock;
    }

    @Override
    public void save(SickNote sickNote) {
        sickNoteRepository.save(sickNote);
    }

    @Override
    public Optional<SickNote> getById(Integer id) {
        return sickNoteRepository.findById(id);
    }

    @Override
    public List<SickNote> getByPersonAndPeriod(Person person, LocalDate from, LocalDate to) {
        return sickNoteRepository.findByPersonAndPeriod(person, from, to);
    }

    @Override
    public List<SickNote> getByPeriod(LocalDate from, LocalDate to) {
        return sickNoteRepository.findByPeriod(from, to);
    }

    @Override
    public List<SickNote> getSickNotesReachingEndOfSickPay() {

        final Settings settings = settingsService.getSettings();
        final SickNoteSettings sickNoteSettings = settings.getSickNoteSettings();

        final LocalDate endDate = ZonedDateTime.now(clock)
            .plusDays(sickNoteSettings.getDaysBeforeEndOfSickPayNotification())
            .toLocalDate();

        return sickNoteRepository.findSickNotesByMinimumLengthAndEndDate(sickNoteSettings.getMaximumSickPayDays(), endDate);
    }

    @Override
    public List<SickNote> getAllActiveByYear(int year) {
        return sickNoteRepository.findAllActiveByYear(year);
    }

    @Override
    public Long getNumberOfPersonsWithMinimumOneSickNote(int year) {
        return sickNoteRepository.findNumberOfPersonsWithMinimumOneSickNote(year);
    }

    @Override
    public List<SickNote> getForStates(List<SickNoteStatus> sickNoteStatuses) {
        return sickNoteRepository.findByStatusIn(sickNoteStatuses);
    }

    @Override
    public List<SickNote> getForStatesAndPersonSince(List<SickNoteStatus> sickNoteStatuses, List<Person> persons, LocalDate since) {
        return sickNoteRepository.findByStatusInAndPersonInAndEndDateIsGreaterThanEqual(sickNoteStatuses, persons, since);
    }
}
