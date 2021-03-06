/*
 * Copyright (c) 2014 Wael Chatila / Icegreen Technologies. All Rights Reserved.
 * This software is released under the Apache license 2.0
 * This file has been used and modified.
 * Original file can be found on http://foedus.sourceforge.net
 */
package com.icegreen.greenmail.smtp.commands;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import com.icegreen.greenmail.smtp.SmtpConnection;
import com.icegreen.greenmail.smtp.SmtpManager;
import com.icegreen.greenmail.smtp.SmtpState;
import com.icegreen.greenmail.user.UserManager;
import com.sun.mail.util.BASE64DecoderStream;


/**
 * AUTH command.
 * <p/>
 * Supported: PLAIN, DIGEST-MD5
 * AUTH mechanism [initial-response]
 * <a href="https://tools.ietf.org/html/rfc4954">RFC4954</a>
 * <a href="https://tools.ietf.org/html/rfc2554">RFC2554</a>
 */
public class AuthCommand
        extends SmtpCommand {
    public static final String AUTH_SUCCEDED = "235 2.7.0  Authentication Succeeded";
    public static final String AUTH_CREDENTIALS_INVALID = "535 5.7.8  Authentication credentials invalid";
    public static final String AUTH_ALREADY_AUTHENTICATED = "503 already authenticated";
    public static final String SMTP_SYNTAX_ERROR = "500 syntax error";
    public static final String SMTP_SERVER_CONTINUATION = "334 "; /* with space !!! */

    public enum AuthMechanism {
        PLAIN
    }

    public static final String SUPPORTED_AUTH_MECHANISM = getValuesWsSeparated();

    @Override
    public void execute(SmtpConnection conn, SmtpState state,
                        SmtpManager manager, String commandLine) throws IOException {
        if (conn.isAuthenticated()) {
            conn.send(AUTH_ALREADY_AUTHENTICATED);
            return;
        }

        //  AUTH mechanism [initial-response]
        final String[] commandParts = commandLine.split(" ");

        if (commandParts.length < 2) {
            conn.send(SMTP_SYNTAX_ERROR + " : expected mechanism but received <" + commandLine + ">");
            return;
        }

        // Check auth mechanism
        final String authMechanismValue = commandParts[1];
        if (!AuthMechanism.PLAIN.name().equalsIgnoreCase(authMechanismValue)) {
            conn.send(SMTP_SYNTAX_ERROR + " : Unsupported auth mechanism " + authMechanismValue +
                    ". Only auth mechanism <" + Arrays.toString(AuthMechanism.values()) + "> supported.");
            return;
        }
        AuthMechanism authMechanism = AuthMechanism.PLAIN;

        // Continuation?
        String initialResponse;
        if (commandParts.length == 2) {
            conn.send(SMTP_SERVER_CONTINUATION );
            initialResponse = conn.receiveLine();
        } else {
            initialResponse = commandParts[2];
        }

        if (authenticate(manager.getUserManager(), authMechanism, initialResponse)) {
            conn.setAuthenticated(true);
            conn.send(AUTH_SUCCEDED);
        } else {
            conn.send(AUTH_CREDENTIALS_INVALID);
        }
    }

    private boolean authenticate(UserManager userManager, AuthMechanism authMechanism, String value) {
        if (AuthMechanism.PLAIN == authMechanism) {
            // authorization-id\0authentication-id\0passwd
            final BASE64DecoderStream stream = new BASE64DecoderStream(
                    new ByteArrayInputStream(value.getBytes(StandardCharsets.US_ASCII)));
            readTillNullChar(stream); // authorizationId Not used
            String authenticationId = readTillNullChar(stream);
            String passwd = readTillNullChar(stream);
            return userManager.test(authenticationId, passwd);
        }
        return false;
    }

    @Deprecated // Remove once JDK baseline is 1.8
    private String readTillNullChar(BASE64DecoderStream stream) {
        try {
            StringBuilder buf = new StringBuilder();
            for (int chr = stream.read(); chr != '\0' && chr > 0; chr = stream.read()) {
                buf.append((char) chr);
            }
            return buf.toString();
        } catch (IOException e) {
            log.error("Can not decode", e);
            return null;
        }
    }

    private static String getValuesWsSeparated() {
        StringBuilder buf = new StringBuilder();
        for (AuthMechanism mechanism : AuthMechanism.values()) {
            buf.append(mechanism);
        }
        return buf.toString();
    }
}