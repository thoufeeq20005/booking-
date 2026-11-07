package com.bookeasy.organization.service;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.bookeasy.common.config.BookEasyProperties;
import com.bookeasy.notification.channel.EmailSender;
import com.bookeasy.notification.channel.EmailSender.EmailMessage;
import com.bookeasy.organization.entity.Organization;
import com.bookeasy.organization.repository.OrganizationOwnerRepository;
import com.bookeasy.organization.repository.OrganizationRepository;
import com.bookeasy.user.entity.User;
import com.bookeasy.user.repository.UserRepository;

/**
 * Emails every owner when their business is submitted, approved, rejected, suspended or reinstated.
 * Runs only after the status change commits; a delivery failure is logged and never undoes the decision.
 */
@Component
public class OrganizationNotifier {

    private static final Logger log = LoggerFactory.getLogger(OrganizationNotifier.class);

    private final OrganizationRepository organizations;
    private final OrganizationOwnerRepository owners;
    private final UserRepository users;
    private final EmailSender emailSender;
    private final MessageSource messages;
    private final BookEasyProperties properties;

    public OrganizationNotifier(OrganizationRepository organizations, OrganizationOwnerRepository owners,
                                UserRepository users, EmailSender emailSender, MessageSource messages,
                                BookEasyProperties properties) {
        this.organizations = organizations;
        this.owners = owners;
        this.users = users;
        this.emailSender = emailSender;
        this.messages = messages;
        this.properties = properties;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onStatusChanged(OrganizationStatusChanged event) {
        Organization org = organizations.findById(event.organizationId()).orElse(null);
        if (org == null) {
            return;
        }
        String key = "email.org." + event.action().name().toLowerCase(Locale.ROOT);
        String link = properties.frontendUrl() + "/owner/organizations/" + org.getId();
        for (var owner : owners.findByOrganizationId(org.getId())) {
            User user = users.findById(owner.getUserId()).orElse(null);
            if (user == null || user.getEmail() == null) {
                continue;
            }
            Locale locale = Locale.forLanguageTag(user.getPreferredLanguage());
            String reason = event.reason() == null || event.reason().isBlank()
                    ? messages.getMessage("email.org.noReason", null, locale) : event.reason();
            try {
                emailSender.send(new EmailMessage(user.getEmail(),
                        messages.getMessage(key + ".subject", new Object[]{org.getName()}, locale),
                        messages.getMessage(key + ".body", new Object[]{user.getFullName(), org.getName(), reason, link},
                                locale)));
            } catch (RuntimeException e) {
                log.warn("Could not email owner {} about organization {} ({})", user.getId(), org.getId(),
                        event.action(), e);
            }
        }
    }
}
