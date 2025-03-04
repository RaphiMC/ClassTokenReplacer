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
package net.raphimc.classtokenreplacer.task;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

public abstract class ReplaceTokensTask extends DefaultTask {

    @InputFiles
    public abstract Property<FileCollection> getClassesDirs();

    @Input
    public abstract MapProperty<String, Object> getProperties();

    @OutputDirectory
    public abstract RegularFileProperty getOutputDir();

    @Inject
    public ReplaceTokensTask(final ObjectFactory objects) {
        this.getProperties().convention(objects.mapProperty(String.class, Object.class));
    }

    @TaskAction
    public void run() throws IOException {
        final File outputDir = this.getOutputDir().get().getAsFile();

        for (File classesDir : this.getClassesDirs().get()) {
            if (!classesDir.isDirectory()) continue;
            final Path root = classesDir.toPath();

            try (Stream<Path> stream = Files.walk(root)) {
                stream.forEach(sourcePath -> {
                    try {
                        final String relative = root.relativize(sourcePath).toString();
                        if (!relative.endsWith(".class")) return;
                        final byte[] bytecode = Files.readAllBytes(sourcePath);
                        final ClassNode classNode = new ClassNode();
                        new ClassReader(bytecode).accept(classNode, 0);
                        final AtomicBoolean hasReplacements = new AtomicBoolean(false);

                        this.handleAnnotations(classNode.visibleAnnotations, hasReplacements);
                        this.handleAnnotations(classNode.invisibleAnnotations, hasReplacements);

                        for (MethodNode methodNode : classNode.methods) {
                            for (AbstractInsnNode insn : methodNode.instructions) {
                                if (insn instanceof LdcInsnNode) {
                                    final LdcInsnNode ldcInsnNode = (LdcInsnNode) insn;
                                    if (ldcInsnNode.cst instanceof String) {
                                        ldcInsnNode.cst = this.replaceTokens((String) ldcInsnNode.cst, hasReplacements);
                                    }
                                } else if (insn instanceof InvokeDynamicInsnNode) {
                                    final InvokeDynamicInsnNode invokeDynamicInsnNode = (InvokeDynamicInsnNode) insn;
                                    if (invokeDynamicInsnNode.bsm.getOwner().equals("java/lang/invoke/StringConcatFactory") && invokeDynamicInsnNode.bsm.getName().equals("makeConcatWithConstants")) {
                                        for (int i = 0; i < invokeDynamicInsnNode.bsmArgs.length; i++) {
                                            if (invokeDynamicInsnNode.bsmArgs[i] instanceof String) {
                                                invokeDynamicInsnNode.bsmArgs[i] = this.replaceTokens((String) invokeDynamicInsnNode.bsmArgs[i], hasReplacements);
                                            }
                                        }
                                    }
                                }
                            }
                            this.handleAnnotations(methodNode.visibleAnnotations, hasReplacements);
                            this.handleAnnotations(methodNode.invisibleAnnotations, hasReplacements);
                        }

                        for (FieldNode fieldNode : classNode.fields) {
                            if (fieldNode.value instanceof String) {
                                fieldNode.value = this.replaceTokens((String) fieldNode.value, hasReplacements);
                            }
                            this.handleAnnotations(fieldNode.visibleAnnotations, hasReplacements);
                            this.handleAnnotations(fieldNode.invisibleAnnotations, hasReplacements);
                        }

                        if (hasReplacements.get()) {
                            final ClassWriter writer = new ClassWriter(0);
                            classNode.accept(writer);
                            final byte[] result = writer.toByteArray();
                            final Path targetPath = outputDir.toPath().resolve(relative);
                            Files.createDirectories(targetPath.getParent());
                            Files.write(targetPath, result);
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

    private void handleAnnotations(final List<AnnotationNode> annotations, final AtomicBoolean hasReplacements) {
        if (annotations == null) return;

        for (AnnotationNode annotationNode : annotations) {
            this.handleAnnotation(annotationNode, hasReplacements);
        }
    }

    private void handleAnnotation(final AnnotationNode annotationNode, final AtomicBoolean hasReplacements) {
        if (annotationNode.values == null) return;

        for (int i = 1; i < annotationNode.values.size(); i += 2) {
            final Object value = annotationNode.values.get(i);
            if (value instanceof String) {
                annotationNode.values.set(i, this.replaceTokens((String) value, hasReplacements));
            } else if (value instanceof List) {
                final List<Object> list = (List<Object>) value;
                for (int j = 0; j < list.size(); j++) {
                    if (list.get(j) instanceof String) {
                        list.set(j, this.replaceTokens((String) list.get(j), hasReplacements));
                    }
                }
            } else if (value instanceof AnnotationNode) {
                this.handleAnnotation((AnnotationNode) value, hasReplacements);
            }
        }
    }

    private String replaceTokens(String str, final AtomicBoolean hasReplacements) {
        final String original = str;
        for (Map.Entry<String, Object> entry : this.getProperties().get().entrySet()) {
            str = str.replace(entry.getKey(), entry.getValue().toString());
            if (!original.equals(str)) {
                hasReplacements.set(true);
            }
        }
        return str;
    }

}
