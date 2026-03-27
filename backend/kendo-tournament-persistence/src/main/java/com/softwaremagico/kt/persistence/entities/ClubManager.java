package com.softwaremagico.kt.persistence.entities;

/*-
 * #%L
 * Kendo Tournament Manager (Persistence)
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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.softwaremagico.kt.persistence.encryption.StringCryptoConverter;
import com.softwaremagico.kt.security.AvailableRole;
import com.softwaremagico.kt.utils.StringUtils;
import jakarta.persistence.Cacheable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * A club manager is a person responsible for managing a specific club. They authenticate
 * using a QR code or passcode instead of a username/password combination.
 */
@Entity
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@Table(name = "club_managers",
        indexes = {
                @Index(name = "ind_club_manager_club", columnList = "club"),
                @Index(name = "ind_club_manager_passcode", columnList = "passcode"),
        })
public class ClubManager extends Element implements UserDetails, IAuthenticatedUser {

    public static final String CLUB_MANAGER_ROLE = "club_manager";
    private static final int PASSCODE_LENGTH = 15;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "club", nullable = false)
    private Club club;

    @Column(name = "name", nullable = false)
    @Convert(converter = StringCryptoConverter.class)
    private String name = "";

    @Column(name = "lastname", nullable = false)
    @Convert(converter = StringCryptoConverter.class)
    private String lastname = "";

    @Column(name = "passcode", length = PASSCODE_LENGTH)
    @Convert(converter = StringCryptoConverter.class)
    private String passcode;

    public ClubManager() {
        super();
    }

    public ClubManager(Club club, String name, String lastname) {
        this();
        this.club = club;
        this.name = name;
        this.lastname = lastname;
    }

    public Club getClub() {
        return club;
    }

    public void setClub(Club club) {
        this.club = club;
    }

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getLastname() {
        return lastname;
    }

    public void setLastname(String lastname) {
        this.lastname = lastname;
    }

    public String getPasscode() {
        return passcode;
    }

    public void setPasscode(String passcode) {
        this.passcode = passcode;
    }

    public void generatePasscode() {
        this.passcode = StringUtils.generateRandomToken(PASSCODE_LENGTH);
    }

    /**
     * The username for a club manager is derived from their ID and club ID to ensure uniqueness.
     */
    @Override
    public String getUsername() {
        return "clubManager_" + getId() + "_" + (club != null ? club.getId() : "");
    }

    @Override
    @JsonIgnore
    public String getPassword() {
        return passcode;
    }

    @Override
    @JsonIgnore
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singleton(new SimpleGrantedAuthority(AvailableRole.CLUB_MANAGER.name()));
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return passcode != null && !passcode.isBlank();
    }

    @Override
    public Set<String> getRoles() {
        return new HashSet<>(Collections.singletonList(CLUB_MANAGER_ROLE));
    }
}
