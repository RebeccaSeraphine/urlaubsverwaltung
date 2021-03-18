Hallo ${recipient.niceName},

<#if (application.holidayReplacement.niceName)?has_content>
morgen beginnt deine Abwesenheit und du wirst vertreten durch:
<#else>
morgen beginnt deine Abwesenheit.
</#if>

<#if (application.holidayReplacement.niceName)?has_content>
${application.holidayReplacement.niceName}<#if (application.holidayReplacementNote)?has_content>, "${application.holidayReplacementNote}"</#if>

</#if>
Da du vom ${application.startDate.format("dd.MM.yyyy")} bis zum ${application.endDate.format("dd.MM.yyyy")} nicht anwesend bist, denke bitte an die Übergabe.
Dazu gehören z.B. Abwesenheitsnotiz, E-Mail- & Telefon-Weiterleitung, Zeiterfassung, etc.

Link zum Antrag: ${baseLinkURL}web/application/${application.id?c}
