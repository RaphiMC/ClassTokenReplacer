/*
 * This file is part of ClassTokenReplacer - https://github.com/RaphiMC/ClassTokenReplacer
 * Copyright (C) 2023-2024 RK_01/RaphiMC and contributors
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
package net.raphimc.classtokenreplacer.task;

import org.gradle.api.DefaultTask;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskAction;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.*;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

public abstract class ReplaceTokensTask extends DefaultTask {

    @Input
    public abstract Property<SourceSet> getSourceSet();

    @Input
    public abstract MapProperty<String, Object> getProperties();

    @Inject
    public ReplaceTokensTask(final ObjectFactory objects) {
        this.getProperties().convention(objects.mapProperty(String.class, Object.class));
    }

    @TaskAction
    public void run() throws IOException {
        final Map<String, Object> properties = this.getProperties().get();

        for (File classesDir : this.getSourceSet().get().getOutput().getClassesDirs()) {
            if (!classesDir.isDirectory()) continue;
            final Path root = classesDir.toPath();

            try (Stream<Path> stream = Files.walk(root)) {
                stream.forEach(path -> {
                    try {
                        final String relative = root.relativize(path).toString();
                        if (!relative.endsWith(".class")) return;
                        final byte[] bytecode = Files.readAllBytes(path);
                        final ClassNode classNode = new ClassNode();
                        new ClassReader(bytecode).accept(classNode, 0);
                        boolean hasReplacements = false;

                        for (MethodNode methodNode : classNode.methods) {
                            for (AbstractInsnNode insn : methodNode.instructions) {
                                if (insn instanceof LdcInsnNode) {
                                    final LdcInsnNode ldcInsnNode = (LdcInsnNode) insn;
                                    if (ldcInsnNode.cst instanceof String) {
                                        String cst = (String) ldcInsnNode.cst;
                                        for (Map.Entry<String, Object> entry : properties.entrySet()) {
                                            cst = cst.replace(entry.getKey(), entry.getValue().toString());
                                            if (!ldcInsnNode.cst.equals(cst)) {
                                                hasReplacements = true;
                                            }
                                        }
                                        ldcInsnNode.cst = cst;
                                    }
                                } else if (insn instanceof InvokeDynamicInsnNode) {
                                    final InvokeDynamicInsnNode invokeDynamicInsnNode = (InvokeDynamicInsnNode) insn;
                                    for (int i = 0; i < invokeDynamicInsnNode.bsmArgs.length; i++) {
                                        if (invokeDynamicInsnNode.bsmArgs[i] instanceof String) {
                                            String value = (String) invokeDynamicInsnNode.bsmArgs[i];
                                            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                                                value = value.replace(entry.getKey(), entry.getValue().toString());
                                                if (!invokeDynamicInsnNode.bsmArgs[i].equals(value)) {
                                                    hasReplacements = true;
                                                }
                                            }
                                            invokeDynamicInsnNode.bsmArgs[i] = value;
                                        }
                                    }
                                }
                            }
                        }
                        for (FieldNode fieldNode : classNode.fields) {
                            if (fieldNode.value instanceof String) {
                                String value = (String) fieldNode.value;
                                for (Map.Entry<String, Object> entry : properties.entrySet()) {
                                    value = value.replace(entry.getKey(), entry.getValue().toString());
                                    if (!fieldNode.value.equals(value)) {
                                        hasReplacements = true;
                                    }
                                }
                                fieldNode.value = value;
                            }
                        }

                        if (hasReplacements) {
                            final ClassWriter writer = new ClassWriter(0);
                            classNode.accept(writer);
                            final byte[] result = writer.toByteArray();
                            Files.write(path, result);
                        }
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            }
        }
    }

    public void property(final String property, final Object value) {
        this.getProperties().put(property, value);
    }

    public void property(final String property, final Provider<Object> value) {
        this.getProperties().put(property, value);
    }

}
