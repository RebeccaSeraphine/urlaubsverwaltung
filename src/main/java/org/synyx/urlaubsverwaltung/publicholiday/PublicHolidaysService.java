package org.synyx.urlaubsverwaltung.publicholiday;

import de.jollyday.Holiday;
import de.jollyday.HolidayManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.synyx.urlaubsverwaltung.period.DayLength;
import org.synyx.urlaubsverwaltung.settings.Settings;
import org.synyx.urlaubsverwaltung.settings.SettingsService;
import org.synyx.urlaubsverwaltung.workingtime.FederalState;
import org.synyx.urlaubsverwaltung.workingtime.WorkingTimeSettings;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.synyx.urlaubsverwaltung.period.DayLength.FULL;
import static org.synyx.urlaubsverwaltung.period.DayLength.ZERO;
import static org.synyx.urlaubsverwaltung.util.DateUtil.isChristmasEve;
import static org.synyx.urlaubsverwaltung.util.DateUtil.isNewYearsEve;

@Component
public class PublicHolidaysService {

    private final HolidayManager manager;
    private final SettingsService settingsService;

    @Autowired
    public PublicHolidaysService(SettingsService settingsService, HolidayManager holidayManager) {
        this.settingsService = settingsService;
        this.manager = holidayManager;
    }

    /**
     * Returns the working duration for a date: may be full day (1.0) for a non public holiday or zero (0.0) for a
     * public holiday. The working duration for Christmas Eve and New Year's Eve are configured in the business
     * properties; normally the working duration for these holidays is a half day (0.5)
     *
     * @param date         to get working duration for
     * @param federalState the federal state to consider holiday settings for
     * @return working duration of the given date
     */
    public BigDecimal getWorkingDurationOfDate(LocalDate date, FederalState federalState) {
        return getAbsenceTypeOfDate(date, federalState).getInverse().getDuration();
    }

    public DayLength getAbsenceTypeOfDate(LocalDate date, FederalState federalState) {

        final Settings settings = settingsService.getSettings();
        final WorkingTimeSettings workingTimeSettings = settings.getWorkingTimeSettings();

        DayLength workingTime = FULL;
        if (isPublicHoliday(date, federalState)) {
            if (isChristmasEve(date)) {
                workingTime = workingTimeSettings.getWorkingDurationForChristmasEve();
            } else if (isNewYearsEve(date)) {
                workingTime = workingTimeSettings.getWorkingDurationForNewYearsEve();
            } else {
                workingTime = ZERO;
            }
        }

        return workingTime.getInverse();
    }

    public List<Holiday> getHolidays(final LocalDate from, final LocalDate to, FederalState federalState) {
        return List.copyOf(manager.getHolidays(from, to, federalState.getCodes()));
    }

    private boolean isPublicHoliday(LocalDate date, FederalState federalState) {
        return manager.isHoliday(date, federalState.getCodes());
    }
}
