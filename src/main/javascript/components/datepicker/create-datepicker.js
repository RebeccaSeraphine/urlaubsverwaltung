import { findWhere } from "underscore";
import { endOfMonth, formatISO, isToday, isWeekend, parseISO } from "date-fns";
import parse from "../../lib/date-fns/parse";
import { defineCustomElements } from "@duetds/date-picker/dist/loader";
import { getJSON } from "../../js/fetch";
import DE from "./locale/de";
import EN from "./locale/en";
import "@duetds/date-picker/dist/collection/themes/default.css";
import "./datepicker.css";
import "../calendar/calendar.css";

// register @duet/datepicker
defineCustomElements(window);

const noop = () => {};

export async function createDatepicker(selector, { urlPrefix, getPersonId, onSelect = noop }) {
  const { localisation } = window.uv.datepicker;

  // currently the UV supports 'en' and 'de' only. and the default is 'de'.
  const dateAdapter = localisation.locale === "en" ? EN.dateAdapter : DE.dateAdapter;
  const dateFormatShort = localisation.locale === "en" ? EN.dateFormatShort : DE.dateFormatShort;

  const duetDateElement = await replaceNativeDateInputWithDuetDatePicker(selector, dateAdapter, localisation);

  const monthElement = duetDateElement.querySelector(".duet-date__select--month");
  const yearElement = duetDateElement.querySelector(".duet-date__select--year");

  const showAbsences = () => {
    // clear all days
    [...duetDateElement.querySelectorAll(".duet-date__day")].forEach((element) =>
      element.setAttribute("class", "duet-date__day"),
    );

    const firstDayOfMonth = `${yearElement.value}-${twoDigit(Number(monthElement.value) + 1)}-01`;
    const lastDayOfMonth = formatISO(endOfMonth(parseISO(firstDayOfMonth)), { representation: "date" });

    const personId = getPersonId();
    if (!personId) {
      return;
    }

    Promise.allSettled([
      getJSON(`${urlPrefix}/persons/${personId}/public-holidays?from=${firstDayOfMonth}&to=${lastDayOfMonth}`).then(
        pick("publicHolidays"),
      ),
      getJSON(`${urlPrefix}/persons/${personId}/absences?from=${firstDayOfMonth}&to=${lastDayOfMonth}`).then(
        pick("absences"),
      ),
    ]).then(([publicHolidays, absences]) => {
      const selectedMonth = Number(monthElement.value);
      const selectedYear = Number(yearElement.value);
      for (let dayElement of [...duetDateElement.querySelectorAll(".duet-date__day")]) {
        const dayAndMonthString = dayElement.querySelector(".duet-date__vhidden").textContent;
        const date = parse(dayAndMonthString, dateFormatShort, new Date());
        // dayAndMonthString is a hard coded duet-date-picker screen-reader-only value which does not contain the year.
        // therefore the parsed date will always be assigned to the current year and we have to adjust it when:
        if (selectedMonth === 0 && date.getMonth() === 11) {
          // datepicker selected month is january, but the rendered day item is december of the previous year
          // (e.g. december 31) to fill the week row.
          date.setFullYear(selectedYear - 1);
        } else if (selectedMonth === 11 && date.getMonth() === 0) {
          // datepicker selected month is december, but the rendered day item is january of the next year
          // (e.g. january 1) to fill the week row.
          date.setFullYear(selectedYear + 1);
        } else {
          date.setFullYear(selectedYear);
        }
        const cssClasses = getCssClassesForDate(date, publicHolidays.value, absences.value);
        dayElement.classList.add(...cssClasses);
      }
    });
  };

  const toggleButton = duetDateElement.querySelector("button.duet-date__toggle");
  toggleButton.addEventListener("click", showAbsences);
  duetDateElement.addEventListener("duetChange", (event) => onSelect(event));

  duetDateElement.querySelector(".duet-date__prev").addEventListener("click", showAbsences);
  duetDateElement.querySelector(".duet-date__next").addEventListener("click", showAbsences);

  monthElement.addEventListener("change", showAbsences);
  yearElement.addEventListener("change", showAbsences);

  return duetDateElement;
}

async function replaceNativeDateInputWithDuetDatePicker(selector, dateAdapter, localization) {
  const dateElement = document.querySelector(selector);
  const duetDateElement = document.createElement("duet-date-picker");

  if (dateElement.value && !dateElement.dataset.isoValue) {
    throw new Error("date input defines a value but no `data-iso-value` attribute is given.");
  }

  duetDateElement.dateAdapter = dateAdapter;
  duetDateElement.localization = localization;

  duetDateElement.setAttribute("style", "--duet-radius=0");
  duetDateElement.setAttribute("class", dateElement.getAttribute("class"));
  duetDateElement.setAttribute("value", dateElement.dataset.isoValue || "");
  duetDateElement.setAttribute("identifier", dateElement.getAttribute("id"));
  dateElement.replaceWith(duetDateElement);

  await waitForDatePickerHydration(duetDateElement);

  // name attribute must be set to the actual visible input element
  // the backend handles the raw user input for progressive enhancement reasons.
  // (german locale is 'dd.MM.yyyy', while english locale would be 'yyyy/MM/dd' for instance)
  const duetDateInputElement = duetDateElement.querySelector("input.duet-date__input");
  duetDateInputElement.setAttribute("name", dateElement.getAttribute("name"));

  for (const [key, value] of Object.entries(dateElement.dataset)) {
    duetDateInputElement.dataset[key] = value;
  }

  return duetDateElement;
}

function waitForDatePickerHydration(rootElement) {
  return new Promise((resolve) => {
    const observer = new MutationObserver((mutationsList) => {
      for (const mutation of mutationsList) {
        if (mutation.target.classList.contains("hydrated")) {
          resolve();
          observer.disconnect();
          return true;
        }
      }
    });
    observer.observe(rootElement, { attributes: true });
  });
}

function getCssClassesForDate(date, publicHolidays, absences) {
  if (date && isWeekend(date)) {
    return ["datepicker-day", "datepicker-day-weekend"];
  } else {
    const dateString = date ? formatISO(date, { representation: "date" }) : "";
    const fitsCriteria = (list, filterAttributes) =>
      Boolean(findWhere(list, { ...filterAttributes, date: dateString }));

    const isPast = () => false;
    const isPublicHolidayFull = () => fitsCriteria(publicHolidays, { absencePeriodName: "FULL" });
    const isPublicHolidayMorning = () => fitsCriteria(publicHolidays, { absencePeriodName: "MORNING" });
    const isPublicHolidayNoon = () => fitsCriteria(publicHolidays, { absencePeriodName: "NOON" });
    const isPersonalHolidayFull = () =>
      fitsCriteria(absences, {
        type: "VACATION",
        absencePeriodName: "FULL",
        status: "WAITING",
      });
    const isPersonalHolidayFullApproved = () =>
      fitsCriteria(absences, {
        type: "VACATION",
        absencePeriodName: "FULL",
        status: "ALLOWED",
      });
    const isPersonalHolidayMorning = () =>
      fitsCriteria(absences, {
        type: "VACATION",
        absencePeriodName: "MORNING",
        status: "WAITING",
      });
    const isPersonalHolidayMorningApproved = () =>
      fitsCriteria(absences, {
        type: "VACATION",
        absencePeriodName: "MORNING",
        status: "ALLOWED",
      });
    const isPersonalHolidayNoon = () =>
      fitsCriteria(absences, {
        type: "VACATION",
        absencePeriodName: "NOON",
        status: "WAITING",
      });
    const isPersonalHolidayNoonApproved = () =>
      fitsCriteria(absences, {
        type: "VACATION",
        absencePeriodName: "NOON",
        status: "ALLOWED",
      });
    const isSickDayFull = () => fitsCriteria(absences, { type: "SICK_NOTE", absencePeriodName: "FULL" });
    const isSickDayMorning = () =>
      fitsCriteria(absences, {
        type: "SICK_NOTE",
        absencePeriodName: "MORNING",
      });
    const isSickDayNoon = () => fitsCriteria(absences, { type: "SICK_NOTE", absencePeriodName: "NOON" });

    return [
      "datepicker-day",
      isToday(date) && "datepicker-day-today",
      isPast() && "datepicker-day-past",
      isPublicHolidayFull() && "datepicker-day-public-holiday-full",
      isPublicHolidayMorning() && "datepicker-day-public-holiday-morning",
      isPublicHolidayNoon() && "datepicker-day-public-holiday-noon",
      isPersonalHolidayFull() && "datepicker-day-personal-holiday-full",
      isPersonalHolidayFullApproved() && "datepicker-day-personal-holiday-full-approved",
      isPersonalHolidayMorning() && "datepicker-day-personal-holiday-morning",
      isPersonalHolidayMorningApproved() && "datepicker-day-personal-holiday-morning-approved",
      isPersonalHolidayNoon() && "datepicker-day-personal-holiday-noon",
      isPersonalHolidayNoonApproved() && "datepicker-day-personal-holiday-noon-approved",
      isSickDayFull() && "datepicker-day-sick-note-full",
      isSickDayMorning() && "datepicker-day-sick-note-morning",
      isSickDayNoon() && "datepicker-day-sick-note-noon",
    ].filter(Boolean);
  }
}

function pick(name) {
  return function (object) {
    return object[name];
  };
}

function twoDigit(nr) {
  return ("0" + nr).slice(-2);
}
