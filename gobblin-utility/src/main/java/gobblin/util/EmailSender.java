/*
 *
 *  * Copyright (C) 2014-2016 LinkedIn Corp. All rights reserved.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 *  * this file except in compliance with the License. You may obtain a copy of the
 *  * License at  http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software distributed
 *  * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 *  * CONDITIONS OF ANY KIND, either express or implied.
 *
 */

package gobblin.util;

import gobblin.configuration.ConfigurationKeys;
import gobblin.password.PasswordManager;

import java.net.InetAddress;
import java.net.UnknownHostException;

import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.typesafe.config.Config;
import org.apache.commons.mail.Email;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.SimpleEmail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A helper class for sending emails.
 */
public class EmailSender {

  private static final Logger LOGGER = LoggerFactory.getLogger(EmailSender.class);

  private final String emailHost;
  private final Optional<Integer> smtpPort;
  private final String emailFrom;
  private final Iterable<String> emailTo;
  private final boolean needsAuth;
  private final String emailUser;
  private final String emailUserPassword;
  private final PasswordManager passwordManager;

  public EmailSender(Config config) {
    emailHost = config.hasPath(ConfigurationKeys.EMAIL_HOST_KEY)
            ?config.getString(ConfigurationKeys.EMAIL_HOST_KEY)
            :ConfigurationKeys.DEFAULT_EMAIL_HOST;

    smtpPort = config.hasPath(ConfigurationKeys.EMAIL_SMTP_PORT_KEY)
            ? Optional.of(config.getInt(ConfigurationKeys.EMAIL_SMTP_PORT_KEY))
            : Optional.<Integer>absent();
    emailFrom = config.getString(ConfigurationKeys.EMAIL_FROM_KEY);
    if (config.hasPath(ConfigurationKeys.EMAIL_USER_KEY) && config.hasPath(ConfigurationKeys.EMAIL_PASSWORD_KEY)) {
      needsAuth = true;
      emailUser = config.getString(ConfigurationKeys.EMAIL_USER_KEY);
      emailUserPassword = config.getString(ConfigurationKeys.EMAIL_PASSWORD_KEY);
      passwordManager = PasswordManager.getInstance();
    }
    else
    {
      needsAuth = false;
      emailUser = null;
      emailUserPassword = null;
      passwordManager = null;
    }

    String emailToList = config.getString(ConfigurationKeys.EMAIL_TOS_KEY);
    emailTo = Splitter.on(',').trimResults().omitEmptyStrings().split(emailToList);

  }

  /**
   * A general method for sending emails.
   *
   * @param subject email subject
   * @param message email message
   * @throws EmailException if there is anything wrong sending the email
   */
  public void sendEmail(String subject, String message) throws EmailException {
    Email email = new SimpleEmail();
    email.setHostName(emailHost);
    if (smtpPort.isPresent()) {
      email.setSmtpPort(smtpPort.get());
    }
    email.setFrom(emailFrom);
    if (needsAuth) {
      email.setAuthentication(emailUser,
              passwordManager.readPassword(emailUserPassword));
    }
    for (String to : emailTo) {
      email.addTo(to);
    }

    String hostName;
    try {
      hostName = InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException uhe) {
      LOGGER.error("Failed to get the host name", uhe);
      hostName = "unknown";
    }

    email.setSubject(subject);
    String fromHostLine = String.format("This email was sent from host: %s%n%n", hostName);
    email.setMsg(fromHostLine + message);
    email.send();
  }

  /**
   * Send a job completion notification email.
   *
   * @param jobId job name
   * @param message email message
   * @param state job state
   * @throws EmailException if there is anything wrong sending the email
   */
  public void sendJobCompletionEmail(String jobId, String message, String state)
          throws EmailException {
    sendEmail(String.format("Gobblin notification: job %s has completed with state %s", jobId, state),
            message);
  }

  /**
   * Send a job cancellation notification email.
   *
   * @param jobId job name
   * @param message email message
   * @throws EmailException if there is anything wrong sending the email
   */
  public void sendJobCancellationEmail(String jobId, String message) throws EmailException {
    sendEmail(String.format("Gobblin notification: job %s has been cancelled", jobId), message);
  }

  /**
   * Send a job failure alert email.
   *
   * @param jobName job name
   * @param message email message
   * @param failures number of consecutive job failures
   * @throws EmailException if there is anything wrong sending the email
   */
  public void sendJobFailureAlertEmail(String jobName, String message, int failures)
          throws EmailException {
    sendEmail(String.format("Gobblin alert: job %s has failed %d %s consecutively in the past", jobName,
            failures, failures > 1 ? "times" : "time"), message);
  }
}
