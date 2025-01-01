/*
 * This file is part of ClassTokenReplacer - https://github.com/RaphiMC/ClassTokenReplacer
 * Copyright (C) 2023-2025 RK_01/RaphiMC and contributors
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.raphimc.classtokenreplacer.extension;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.MapProperty;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;

public class ClassTokenReplacerExtensionImpl implements ClassTokenReplacerExtension {

    private final MapProperty<String, Object> properties;

    @Inject
    public ClassTokenReplacerExtensionImpl(final ObjectFactory objects) {
        this.properties = objects.mapProperty(String.class, Object.class);
    }

    @Override
    @NotNull
    public MapProperty<String, Object> getProperties() {
        return this.properties;
    }

}
