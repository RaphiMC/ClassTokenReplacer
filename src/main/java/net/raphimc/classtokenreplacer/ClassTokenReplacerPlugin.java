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
package net.raphimc.classtokenreplacer;

import net.raphimc.classtokenreplacer.extension.ClassTokenReplacerExtension;
import net.raphimc.classtokenreplacer.extension.ClassTokenReplacerExtensionImpl;
import net.raphimc.classtokenreplacer.task.ReplaceTokensTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.RegularFile;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.tasks.Jar;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class ClassTokenReplacerPlugin implements Plugin<Project> {

    @Override
    public void apply(@NotNull Project project) {
        final SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
        sourceSets.configureEach(set -> {
            final ClassTokenReplacerExtension extension = set.getExtensions().create(ClassTokenReplacerExtension.class, "classTokenReplacer", ClassTokenReplacerExtensionImpl.class, project.getObjects());

            final TaskProvider<ReplaceTokensTask> replaceTokensTaskProvider = project.getTasks().register(set.getTaskName("replace", "tokens"), ReplaceTokensTask.class, task -> {
                task.getClassesDirs().set(set.getOutput().getClassesDirs());
                task.getProperties().set(extension.getProperties());
                task.getReplaceInPlace().set(extension.getReplaceInPlace());
                task.getOutputDir().set(project.getLayout().getBuildDirectory().dir("classTokenReplacer/" + set.getName()).get().getAsFile());
            });
            final ReplaceTokensTask replaceTokensTask = replaceTokensTaskProvider.get();
            replaceTokensTask.dependsOn(set.getClassesTaskName());

            if (!extension.getReplaceInPlace().get()) {
                final Jar jarTask = (Jar) project.getTasks().findByName(set.getJarTaskName());
                if (jarTask != null) {
                    final RegularFile replacedClassesDir = replaceTokensTask.getOutputDir().get();
                    jarTask.dependsOn(replaceTokensTask);
                    jarTask.from(replacedClassesDir);
                    jarTask.exclude(fileTreeElement -> {
                        final File modifiedFile = fileTreeElement.getRelativePath().getFile(replacedClassesDir.getAsFile());
                        if (modifiedFile.equals(fileTreeElement.getFile())) {
                            return false;
                        }
                        return modifiedFile.isFile();
                    });
                }
            } else {
                final Task classesTask = project.getTasks().findByName(set.getClassesTaskName());
                if (classesTask != null) {
                    classesTask.finalizedBy(replaceTokensTask);
                }
            }
        });
    }

}
