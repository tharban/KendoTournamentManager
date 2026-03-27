package com.softwaremagico.kt.rest.services;

/*-
 * #%L
 * Kendo Tournament Manager (Rest)
 * %%
 * Copyright (C) 2021 - 2026 Softwaremagico
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */

import com.softwaremagico.kt.core.controller.models.ParticipantDTO;
import com.softwaremagico.kt.core.controller.models.QrCodeDTO;
import com.softwaremagico.kt.core.converters.ParticipantConverter;
import com.softwaremagico.kt.core.converters.models.ParticipantConverterRequest;
import com.softwaremagico.kt.core.controller.QrController;
import com.softwaremagico.kt.core.providers.ClubManagerProvider;
import com.softwaremagico.kt.core.providers.ClubProvider;
import com.softwaremagico.kt.core.providers.ParticipantProvider;
import com.softwaremagico.kt.persistence.encryption.KeyProperty;
import com.softwaremagico.kt.persistence.entities.Club;
import com.softwaremagico.kt.persistence.entities.ClubManager;
import com.softwaremagico.kt.rest.exceptions.BadRequestException;
import com.softwaremagico.kt.rest.exceptions.UserNotFoundException;
import com.softwaremagico.kt.rest.security.AuthApi;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/club-managers")
public class ClubManagerServices {

    private final ClubManagerProvider clubManagerProvider;
    private final ClubProvider clubProvider;
    private final ParticipantProvider participantProvider;
    private final ParticipantConverter participantConverter;
    private final QrController qrController;

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${server.domain:localhost}")
    private String machineDomain;

    @Value("${server.schema:http}")
    private String schema;

    @Value("${spring.mail.from:noreply@kendotournament.com}")
    private String mailFrom;

    @Value("${club.manager.frontend.port:#{null}}")
    private Integer frontendPort;

    @Autowired
    public ClubManagerServices(ClubManagerProvider clubManagerProvider, ClubProvider clubProvider,
                               ParticipantProvider participantProvider, ParticipantConverter participantConverter,
                               QrController qrController) {
        this.clubManagerProvider = clubManagerProvider;
        this.clubProvider = clubProvider;
        this.participantProvider = participantProvider;
        this.participantConverter = participantConverter;
        this.qrController = qrController;
    }

    /**
     * Generates or regenerates a passcode for the club manager of the specified club and sends an invitation
     * email containing a QR code and direct login link.
     */
    @PreAuthorize("hasAnyAuthority(@securityService.editorPrivilege, @securityService.adminPrivilege)")
    @Operation(summary = "Sends an invitation email with QR code and login link to the club's email address.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping(value = "/{clubId}/send-email", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public void sendClubManagerEmail(
            @Parameter(description = "Id of an existing club", required = true)
            @PathVariable("clubId") Integer clubId,
            @RequestHeader(value = AuthApi.SESSION_HEADER, required = false) String session,
            Authentication authentication,
            HttpServletRequest request) {
        final Club club = clubProvider.get(clubId)
                .orElseThrow(() -> new BadRequestException(this.getClass(), "Club not found with id: " + clubId));

        if (club.getEmail() == null || club.getEmail().isBlank()) {
            throw new BadRequestException(this.getClass(), "Club has no email address configured.");
        }

        sendEmailToClub(club, request);
    }

    /**
     * Public endpoint: given an email address, finds the matching club and sends a new login link.
     */
    @Operation(summary = "Requests a new login link for the club manager email address.")
    @PostMapping(value = "/public/request-link", produces = MediaType.APPLICATION_JSON_VALUE,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public void requestClubManagerLink(@RequestBody ClubManagerEmailRequest emailRequest,
                                       HttpServletRequest request) {
        if (emailRequest.getEmail() == null || emailRequest.getEmail().isBlank()) {
            throw new BadRequestException(this.getClass(), "Email address must be provided.");
        }
        // Always return 200 to avoid email enumeration attacks
        findClubByEmail(emailRequest.getEmail()).ifPresent(club -> sendEmailToClub(club, request));
    }

    /**
     * Returns the current club manager's information (used by the frontend after login).
     */
    @PreAuthorize("hasAuthority(@securityService.clubManagerPrivilege)")
    @Operation(summary = "Returns the current club manager's club information.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping(value = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
    public ClubManagerResponse getCurrentClubManager(Authentication authentication,
                                                     HttpServletRequest request) {
        final ClubManager clubManager = clubManagerProvider.findByUsername(authentication.getName())
                .orElseThrow(() -> new UserNotFoundException(this.getClass(), "Club manager not found for current session."));
        final ClubManagerResponse response = new ClubManagerResponse();
        response.setClubId(clubManager.getClub().getId());
        response.setClubName(clubManager.getClub().getName());
        return response;
    }

    /**
     * Returns the list of participants belonging to the specified club.
     * Club managers can only access their own club.
     */
    @PreAuthorize("hasAnyAuthority(@securityService.clubManagerPrivilege, @securityService.editorPrivilege,"
            + " @securityService.adminPrivilege, @securityService.viewerPrivilege)")
    @Operation(summary = "Gets all participants from a club.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping(value = "/{clubId}/participants", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<ParticipantDTO> getClubParticipants(
            @Parameter(description = "Id of an existing club", required = true)
            @PathVariable("clubId") Integer clubId,
            Authentication authentication,
            HttpServletRequest request) {
        final Club club = clubProvider.get(clubId)
                .orElseThrow(() -> new BadRequestException(this.getClass(), "Club not found with id: " + clubId));

        // If club manager, verify they can only see their own club
        if (authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "CLUB_MANAGER".equals(a.getAuthority()))) {
            final ClubManager clubManager = clubManagerProvider.findByUsername(authentication.getName())
                    .orElseThrow(() -> new UserNotFoundException(this.getClass(), "Club manager not found."));
            if (!clubManager.getClub().getId().equals(clubId)) {
                throw new BadRequestException(this.getClass(), "Club manager can only view their own club's members.");
            }
        }

        return participantProvider.get(club).stream()
                .map(p -> participantConverter.convert(new ParticipantConverterRequest(p)))
                .toList();
    }

    /**
     * Generates a QR code for the club manager login link without regenerating the passcode.
     */
    @PreAuthorize("hasAnyAuthority(@securityService.editorPrivilege, @securityService.adminPrivilege)")
    @Operation(summary = "Generates a QR code for the club manager login link.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping(value = "/{clubId}/qr", produces = MediaType.APPLICATION_JSON_VALUE)
    public QrCodeDTO getClubManagerQrCode(
            @Parameter(description = "Id of an existing club", required = true)
            @PathVariable("clubId") Integer clubId,
            @RequestParam(name = "nightMode", required = false) Optional<Boolean> nightMode,
            HttpServletRequest request, HttpServletResponse response) {
        final Club club = clubProvider.get(clubId)
                .orElseThrow(() -> new BadRequestException(this.getClass(), "Club not found with id: " + clubId));

        final ClubManager clubManager = getOrCreateClubManager(club);
        if (clubManager.getPasscode() == null || clubManager.getPasscode().isBlank()) {
            clubManagerProvider.generatePasscode(clubManager);
        }
        final String link = buildLoginLink(clubManager, request);
        return qrController.generateQrCode(link, nightMode.orElse(false));
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private void sendEmailToClub(Club club, HttpServletRequest request) {
        final ClubManager clubManager = getOrCreateClubManager(club);
        // Regenerate passcode on every send to invalidate previous link
        clubManagerProvider.generatePasscode(clubManager);

        final String link = buildLoginLink(clubManager, request);
        final QrCodeDTO qrCode = qrController.generateQrCode(link, false);

        if (mailSender != null) {
            sendEmail(club.getEmail(), club.getName(), link, qrCode);
        }
    }

    private ClubManager getOrCreateClubManager(Club club) {
        final List<ClubManager> existing = clubManagerProvider.findByClub(club);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        final ClubManager clubManager = new ClubManager(club, club.getName(), "");
        return clubManagerProvider.save(clubManager);
    }

    private String buildLoginLink(ClubManager clubManager, HttpServletRequest request) {
        final int port = frontendPort != null ? frontendPort : request.getServerPort();
        final String domain = "localhost".equals(machineDomain) ? request.getServerName() : machineDomain;
        return schema + "://" + domain + ":" + port + "/#/club-manager/login?passcode="
                + clubManager.getPasscode();
    }

    private void sendEmail(String to, String clubName, String link, QrCodeDTO qrCode) {
        try {
            final MimeMessage message = mailSender.createMimeMessage();
            final MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(mailFrom);
            helper.setTo(to);
            helper.setSubject("Club Manager Access - " + clubName);
            final String qrBase64 = Base64.getEncoder().encodeToString(qrCode.getData());
            helper.setText(buildEmailHtml(clubName, link, qrBase64), true);
            mailSender.send(message);
        } catch (Exception e) {
            throw new BadRequestException(this.getClass(), "Failed to send email: " + e.getMessage());
        }
    }

    private String buildEmailHtml(String clubName, String link, String qrBase64) {
        return "<html><body style='font-family:Arial,sans-serif;max-width:600px;margin:auto'>"
                + "<h2>Club Manager Access - " + clubName + "</h2>"
                + "<p>You have been granted access to view your club members' statistics.</p>"
                + "<p>Click the link below or scan the QR code to log in:</p>"
                + "<p><a href='" + link + "' style='background:#001239;color:#fff;padding:10px 20px;"
                + "text-decoration:none;border-radius:4px'>Access Club Members</a></p>"
                + "<br/>"
                + "<img src='data:image/png;base64," + qrBase64 + "' alt='QR Code' "
                + "style='width:200px;height:200px'/>"
                + "<p style='color:#666;font-size:12px'>This link contains a one-time passcode. "
                + "Request a new link if this one has expired.</p>"
                + "</body></html>";
    }

    private Optional<Club> findClubByEmail(String email) {
        final List<Club> clubs = clubProvider.getAll();
        return clubs.stream()
                .filter(c -> c.getEmail() != null && email.equalsIgnoreCase(c.getEmail()))
                .findFirst();
    }

    // ── Inner DTOs ───────────────────────────────────────────────────────────

    public static class ClubManagerEmailRequest {
        private String email;

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }

    public static class ClubManagerResponse {
        private Integer clubId;
        private String clubName;

        public Integer getClubId() {
            return clubId;
        }

        public void setClubId(Integer clubId) {
            this.clubId = clubId;
        }

        public String getClubName() {
            return clubName;
        }

        public void setClubName(String clubName) {
            this.clubName = clubName;
        }
    }
}


import com.softwaremagico.kt.core.controller.QrController;
import com.softwaremagico.kt.core.controller.models.ClubDTO;
import com.softwaremagico.kt.core.controller.models.ParticipantDTO;
import com.softwaremagico.kt.core.controller.models.QrCodeDTO;
import com.softwaremagico.kt.core.providers.ClubManagerProvider;
import com.softwaremagico.kt.core.providers.ClubProvider;
import com.softwaremagico.kt.core.providers.ParticipantProvider;
import com.softwaremagico.kt.persistence.entities.Club;
import com.softwaremagico.kt.persistence.entities.ClubManager;
import com.softwaremagico.kt.persistence.encryption.KeyProperty;
import com.softwaremagico.kt.rest.exceptions.BadRequestException;
import com.softwaremagico.kt.rest.exceptions.UserNotFoundException;
import com.softwaremagico.kt.rest.security.AuthApi;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/club-managers")
public class ClubManagerServices {

    private final ClubManagerProvider clubManagerProvider;
    private final ClubProvider clubProvider;
    private final ParticipantProvider participantProvider;
    private final QrController qrController;

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${server.domain:localhost}")
    private String machineDomain;

    @Value("${server.schema:http}")
    private String schema;

    @Value("${spring.mail.from:noreply@kendotournament.com}")
    private String mailFrom;

    @Value("${club.manager.frontend.port:#{null}}")
    private Integer frontendPort;

    @Autowired
    public ClubManagerServices(ClubManagerProvider clubManagerProvider, ClubProvider clubProvider,
                               ParticipantProvider participantProvider, QrController qrController) {
        this.clubManagerProvider = clubManagerProvider;
        this.clubProvider = clubProvider;
        this.participantProvider = participantProvider;
        this.qrController = qrController;
    }

    /**
     * Generates or regenerates a passcode for the club manager of the specified club and sends an invitation
     * email containing a QR code and direct login link.
     */
    @PreAuthorize("hasAnyAuthority(@securityService.editorPrivilege, @securityService.adminPrivilege)")
    @Operation(summary = "Sends an invitation email with QR code and login link to the club's email address.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping(value = "/{clubId}/send-email", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public void sendClubManagerEmail(
            @Parameter(description = "Id of an existing club", required = true)
            @PathVariable("clubId") Integer clubId,
            @RequestHeader(value = AuthApi.SESSION_HEADER, required = false) String session,
            Authentication authentication,
            HttpServletRequest request) {
        final Club club = clubProvider.get(clubId)
                .orElseThrow(() -> new BadRequestException(this.getClass(), "Club not found with id: " + clubId));

        if (club.getEmail() == null || club.getEmail().isBlank()) {
            throw new BadRequestException(this.getClass(), "Club has no email address configured.");
        }

        sendEmailToClub(club, request);
    }

    /**
     * Public endpoint: given an email address, finds the matching club and sends a new login link.
     */
    @Operation(summary = "Requests a new login link for the club manager email address.")
    @PostMapping(value = "/public/request-link", produces = MediaType.APPLICATION_JSON_VALUE,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public void requestClubManagerLink(@RequestBody ClubManagerEmailRequest emailRequest,
                                        HttpServletRequest request) {
        if (emailRequest.getEmail() == null || emailRequest.getEmail().isBlank()) {
            throw new BadRequestException(this.getClass(), "Email address must be provided.");
        }

        final Optional<Club> matchingClub = findClubByEmail(emailRequest.getEmail());
        // Always return 200 to avoid email enumeration attacks
        matchingClub.ifPresent(club -> sendEmailToClub(club, request));
    }

    /**
     * Returns the current club manager's information (used by the frontend after login).
     */
    @PreAuthorize("hasAuthority(@securityService.clubManagerPrivilege)")
    @Operation(summary = "Returns the current club manager's club information.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping(value = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
    public ClubManagerResponse getCurrentClubManager(Authentication authentication,
                                                     HttpServletRequest request) {
        final ClubManager clubManager = clubManagerProvider.findByUsername(authentication.getName())
                .orElseThrow(() -> new UserNotFoundException(this.getClass(), "Club manager not found for current session."));
        final ClubManagerResponse response = new ClubManagerResponse();
        response.setClubId(clubManager.getClub().getId());
        response.setClubName(clubManager.getClub().getName());
        return response;
    }

    /**
     * Returns the list of participants belonging to the club of the current club manager.
     */
    @PreAuthorize("hasAnyAuthority(@securityService.clubManagerPrivilege, @securityService.editorPrivilege,"
            + " @securityService.adminPrivilege, @securityService.viewerPrivilege)")
    @Operation(summary = "Gets all participants from a club.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping(value = "/{clubId}/participants", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<ParticipantDTO> getClubParticipants(
            @Parameter(description = "Id of an existing club", required = true)
            @PathVariable("clubId") Integer clubId,
            Authentication authentication,
            HttpServletRequest request) {
        final Club club = clubProvider.get(clubId)
                .orElseThrow(() -> new BadRequestException(this.getClass(), "Club not found with id: " + clubId));

        // If club manager, verify they can only see their own club
        if (authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "CLUB_MANAGER".equals(a.getAuthority()))) {
            final ClubManager clubManager = clubManagerProvider.findByUsername(authentication.getName())
                    .orElseThrow(() -> new UserNotFoundException(this.getClass(), "Club manager not found."));
            if (!clubManager.getClub().getId().equals(clubId)) {
                throw new BadRequestException(this.getClass(), "Club manager can only view their own club's members.");
            }
        }

        return participantProvider.get(club).stream()
                .map(p -> {
                    final ParticipantDTO dto = new ParticipantDTO();
                    dto.setId(p.getId());
                    dto.setName(p.getName());
                    dto.setLastname(p.getLastname());
                    dto.setIdCard(p.getIdCard());
                    dto.setHasAvatar(p.getHasAvatar() != null && p.getHasAvatar());
                    final ClubDTO clubDTO = new ClubDTO();
                    clubDTO.setId(club.getId());
                    clubDTO.setName(club.getName());
                    dto.setClub(clubDTO);
                    return dto;
                })
                .toList();
    }

    /**
     * Generates a QR code for a club manager login link.
     */
    @PreAuthorize("hasAnyAuthority(@securityService.editorPrivilege, @securityService.adminPrivilege)")
    @Operation(summary = "Generates a QR code for the club manager login link.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping(value = "/{clubId}/qr", produces = MediaType.APPLICATION_JSON_VALUE)
    public QrCodeDTO getClubManagerQrCode(
            @Parameter(description = "Id of an existing club", required = true)
            @PathVariable("clubId") Integer clubId,
            @RequestParam(name = "nightMode", required = false) Optional<Boolean> nightMode,
            HttpServletRequest request, HttpServletResponse response) {
        final Club club = clubProvider.get(clubId)
                .orElseThrow(() -> new BadRequestException(this.getClass(), "Club not found with id: " + clubId));

        // Ensure ClubManager exists with a valid passcode
        final ClubManager clubManager = getOrCreateClubManager(club);
        final String link = buildLoginLink(clubManager, request);
        return qrController.generateQrCode(link, nightMode.orElse(false));
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private void sendEmailToClub(Club club, HttpServletRequest request) {
        final ClubManager clubManager = getOrCreateClubManager(club);
        // Regenerate passcode on every send
        clubManagerProvider.generatePasscode(clubManager);

        final String link = buildLoginLink(clubManager, request);
        final QrCodeDTO qrCode = qrController.generateQrCode(link, false);

        if (mailSender != null) {
            sendEmail(club.getEmail(), club.getName(), link, qrCode);
        }
    }

    private ClubManager getOrCreateClubManager(Club club) {
        final List<ClubManager> existing = clubManagerProvider.findByClub(club);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        // Auto-create a club manager for this club
        final ClubManager clubManager = new ClubManager(club, club.getName(), "");
        return clubManagerProvider.save(clubManager);
    }

    private String buildLoginLink(ClubManager clubManager, HttpServletRequest request) {
        final int port = frontendPort != null ? frontendPort : request.getServerPort();
        final String domain = machineDomain.equals("localhost") ? request.getServerName() : machineDomain;
        return schema + "://" + domain + ":" + port + "/#/club-manager/login?passcode="
                + clubManager.getPasscode();
    }

    private void sendEmail(String to, String clubName, String link, QrCodeDTO qrCode) {
        try {
            final MimeMessage message = mailSender.createMimeMessage();
            final MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(mailFrom);
            helper.setTo(to);
            helper.setSubject("Club Manager Access - " + clubName);

            final String qrBase64 = Base64.getEncoder().encodeToString(qrCode.getData());
            final String html = buildEmailHtml(clubName, link, qrBase64);
            helper.setText(html, true);
            mailSender.send(message);
        } catch (Exception e) {
            throw new BadRequestException(this.getClass(), "Failed to send email: " + e.getMessage());
        }
    }

    private String buildEmailHtml(String clubName, String link, String qrBase64) {
        return "<html><body style='font-family:Arial,sans-serif;max-width:600px;margin:auto'>"
                + "<h2>Club Manager Access - " + clubName + "</h2>"
                + "<p>You have been granted access to view your club members' statistics.</p>"
                + "<p>Click the link below or scan the QR code to log in:</p>"
                + "<p><a href='" + link + "' style='background:#001239;color:#fff;padding:10px 20px;"
                + "text-decoration:none;border-radius:4px'>Access Club Members</a></p>"
                + "<br/>"
                + "<img src='data:image/png;base64," + qrBase64 + "' alt='QR Code' "
                + "style='width:200px;height:200px'/>"
                + "<p style='color:#666;font-size:12px'>This link contains a one-time passcode. "
                + "Request a new link if this one has expired.</p>"
                + "</body></html>";
    }

    private Optional<Club> findClubByEmail(String email) {
        final List<Club> clubs = clubProvider.getAll();
        if (KeyProperty.getDatabaseEncryptionKey() != null && !KeyProperty.getDatabaseEncryptionKey().isBlank()) {
            return clubs.stream()
                    .filter(c -> email.equalsIgnoreCase(c.getEmail()))
                    .findFirst();
        }
        return clubs.stream()
                .filter(c -> email.equalsIgnoreCase(c.getEmail()))
                .findFirst();
    }

    // ── Inner DTOs ───────────────────────────────────────────────────────────

    public static class ClubManagerEmailRequest {
        private String email;

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }

    public static class ClubManagerResponse {
        private Integer clubId;
        private String clubName;

        public Integer getClubId() {
            return clubId;
        }

        public void setClubId(Integer clubId) {
            this.clubId = clubId;
        }

        public String getClubName() {
            return clubName;
        }

        public void setClubName(String clubName) {
            this.clubName = clubName;
        }
    }
}
