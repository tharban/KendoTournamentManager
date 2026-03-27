package com.softwaremagico.kt.core.providers;

/*-
 * #%L
 * Kendo Tournament Manager (Core)
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

import com.softwaremagico.kt.persistence.encryption.KeyProperty;
import com.softwaremagico.kt.persistence.entities.Club;
import com.softwaremagico.kt.persistence.entities.ClubManager;
import com.softwaremagico.kt.persistence.repositories.ClubManagerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ClubManagerProvider extends CrudProvider<ClubManager, Integer, ClubManagerRepository> {

    @Autowired
    public ClubManagerProvider(ClubManagerRepository repository) {
        super(repository);
    }

    public List<ClubManager> findByClub(Club club) {
        return getRepository().findByClub(club);
    }

    public Optional<ClubManager> findByPasscode(String passcode) {
        if (passcode == null || passcode.isBlank()) {
            return Optional.empty();
        }
        // If encryption is enabled, the passcode column is encrypted and direct DB lookup works
        // because the JPA converter encrypts the query parameter too.
        // However, for safety with encryption key changes, we fall back to in-memory comparison.
        if (KeyProperty.getDatabaseEncryptionKey() != null && !KeyProperty.getDatabaseEncryptionKey().isBlank()) {
            final List<ClubManager> all = getRepository().findAll();
            for (final ClubManager clubManager : all) {
                if (passcode.equals(clubManager.getPasscode())) {
                    return Optional.of(clubManager);
                }
            }
            return Optional.empty();
        }
        return getRepository().findByPasscode(passcode);
    }

    /**
     * Finds a club manager by their derived username (format: "clubManager_{id}_{clubId}").
     */
    public Optional<ClubManager> findByUsername(String username) {
        if (username == null || !username.startsWith("clubManager_")) {
            return Optional.empty();
        }
        try {
            final String[] parts = username.split("_");
            if (parts.length >= 2) {
                final int id = Integer.parseInt(parts[1]);
                return getRepository().findById(id);
            }
        } catch (NumberFormatException ignored) {
            // Invalid username format
        }
        return Optional.empty();
    }

    public ClubManager generatePasscode(ClubManager clubManager) {
        do {
            clubManager.generatePasscode();
        } while (getRepository().countByPasscode(clubManager.getPasscode()) > 0);
        return save(clubManager);
    }
}
