
package org.synyx.urlaubsverwaltung.core.startup;

import org.apache.log4j.Logger;

import org.springframework.beans.factory.annotation.Value;

import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;


/**
 * This service is executed every time the application is started.
 *
 * @author  Aljona Murygina - murygina@synyx.de
 */
@Service
public class StartupService {

    private static final Logger LOG = Logger.getLogger(StartupService.class);

    @Value("${db.username}")
    private String dbUser;

    @Value("${db.url}")
    private String dbUrl;

    @Value("${email.manager}")
    private String emailManager;

    @PostConstruct
    public void logStartupInfo() {

        LOG.info("Using database " + dbUrl + " with user " + dbUser);
        LOG.info("Using following email address for technical notification: " + emailManager);
    }
}
