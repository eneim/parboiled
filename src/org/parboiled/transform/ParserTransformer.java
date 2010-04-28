/*
 * Copyright (C) 2009 Mathias Doenitz
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.parboiled.transform;

import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.ClassWriter;

import java.util.List;

import static org.parboiled.transform.AsmUtils.*;

public class ParserTransformer {

    private static final Object lock = new Object();

    private ParserTransformer() {}

    @SuppressWarnings({"unchecked"})
    public static <T> Class<? extends T> transformParser(@NotNull Class<T> parserClass) throws Exception {
        synchronized (lock) {
            // first check whether we did not already create and load the extension of the given parser class
            Class<?> extendedClass = findLoadedClass(
                    getExtendedParserClassName(parserClass.getName()), parserClass.getClassLoader()
            );
            return (Class<? extends T>)
                    (extendedClass != null ? extendedClass : extendParserClass(parserClass).getExtendedClass());
        }
    }

    static ParserClassNode extendParserClass(Class<?> parserClass) throws Exception {
        ParserClassNode classNode = new ParserClassNode(parserClass);
        new ClassNodeInitializer().process(classNode);
        runMethodTransformers(classNode);
        new ConstructorGenerator().process(classNode);
        defineExtendedParserClass(classNode);
        return classNode;
    }

    @SuppressWarnings({"unchecked"})
    private static void runMethodTransformers(ParserClassNode classNode) throws Exception {
        List<RuleMethodProcessor> methodProcessors = createRuleMethodProcessors();
        for (RuleMethod ruleMethod : classNode.getRuleMethods()) {
            for (RuleMethodProcessor methodProcessor : methodProcessors) {
                if (methodProcessor.appliesTo(ruleMethod)) {
                    methodProcessor.process(classNode, ruleMethod);
                }
            }
            classNode.methods.add(ruleMethod);
        }
    }

    static List<RuleMethodProcessor> createRuleMethodProcessors() {
        return ImmutableList.of(
                new UnusedLabelsRemover(),
                new ReturnInstructionUnifier(),
                new InstructionGraphCreator(),
                new ImplicitActionsConverter(),
                new InstructionGroupCreator(),
                new InstructionGroupPreparer(),
                new CaptureClassGenerator(false),
                new ActionClassGenerator(false),
                new RuleMethodRewriter(),
                new VarFramingGenerator(),
                new SuperCallRewriter(),
                new LabellingGenerator(),
                new SuppressNodeGenerator(),
                new SkipNodeGenerator(),
                new CachingGenerator()
        );
    }

    private static void defineExtendedParserClass(ParserClassNode classNode) {
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classNode.accept(classWriter);
        classNode.setClassCode(classWriter.toByteArray());
        classNode.setExtendedClass(loadClass(
                classNode.name.replace('/', '.'),
                classNode.getClassCode(),
                classNode.getParentClass().getClassLoader()
        ));
    }

}