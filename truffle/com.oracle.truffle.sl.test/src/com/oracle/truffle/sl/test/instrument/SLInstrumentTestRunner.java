/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 * 
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 * 
 * (a) the Software, and
 * 
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 * 
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 * 
 * This license is subject to the following condition:
 * 
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.sl.test.instrument;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;

import org.junit.*;
import org.junit.internal.*;
import org.junit.runner.*;
import org.junit.runner.manipulation.*;
import org.junit.runner.notification.*;
import org.junit.runners.*;
import org.junit.runners.model.*;

import com.oracle.truffle.api.instrument.*;
import com.oracle.truffle.api.instrument.impl.*;
import com.oracle.truffle.api.vm.*;
import com.oracle.truffle.sl.nodes.instrument.*;
import com.oracle.truffle.sl.nodes.local.*;
import com.oracle.truffle.sl.test.instrument.SLInstrumentTestRunner.InstrumentTestCase;

/**
 * This class builds and executes the tests for instrumenting SL. Although much of this class is
 * written with future automation in mind, at the moment the tests that are created are hard-coded
 * according to the file name of the test. To be automated, an automatic way of generating both the
 * node visitor and the node prober is necessary.
 *
 * Testing is done via JUnit via comparing execution outputs with expected outputs.
 */
public final class SLInstrumentTestRunner extends ParentRunner<InstrumentTestCase> {

    private static final String SOURCE_SUFFIX = ".sl";
    private static final String INPUT_SUFFIX = ".input";
    private static final String OUTPUT_SUFFIX = ".output";
    private static final String ASSIGNMENT_VALUE_SUFFIX = "_assnCount";

    private static final String LF = System.getProperty("line.separator");

    static class InstrumentTestCase {
        protected final Description name;
        protected final Path path;
        protected final String baseName;
        protected final String sourceName;
        protected final String testInput;
        protected final String expectedOutput;
        protected String actualOutput;

        protected InstrumentTestCase(Class<?> testClass, String baseName, String sourceName, Path path, String testInput, String expectedOutput) {
            this.name = Description.createTestDescription(testClass, baseName);
            this.baseName = baseName;
            this.sourceName = sourceName;
            this.path = path;
            this.testInput = testInput;
            this.expectedOutput = expectedOutput;
        }
    }

    private final List<InstrumentTestCase> testCases;

    public SLInstrumentTestRunner(Class<?> testClass) throws InitializationError {
        super(testClass);
        final SLStandardASTProber prober = new SLStandardASTProber();
        Probe.registerASTProber(prober);
        try {
            testCases = createTests(testClass);
        } catch (IOException e) {
            throw new InitializationError(e);
        } finally {
            Probe.unregisterASTProber(prober);
        }
    }

    @Override
    protected List<InstrumentTestCase> getChildren() {
        return testCases;
    }

    @Override
    protected Description describeChild(InstrumentTestCase child) {
        return child.name;
    }

    /**
     * Tests are created based on the files that exist in the directory specified in the passed in
     * annotation. Each test must have a source file and an expected output file. Optionally, each
     * test can also include an input file. Source files have an ".sl" extension. Expected output
     * have a ".output" extension. Input files have an ".input" extension. All these files must
     * share the same base name to be correctly grouped. For example: "test1_assnCount.sl",
     * "test1_assnCount.output" and "test1_assnCount.input" would all be used to create a single
     * test called "test1_assnCount".
     *
     * This method iterates over the files in the directory and creates a new InstrumentTestCase for
     * each group of related files. Each file is also expected to end with an identified at the end
     * of the base name to indicate what visitor needs to be attached. Currently, visitors are hard
     * coded to work on specific lines, so the code here is not currently generalizable.
     *
     * @param c The annotation containing the directory with tests
     * @return A list of {@link InstrumentTestCase}s to run.
     * @throws IOException If the directory is invalid.
     * @throws InitializationError If no directory is provided.
     *
     * @see #runChild(InstrumentTestCase, RunNotifier)
     */
    protected static List<InstrumentTestCase> createTests(final Class<?> c) throws IOException, InitializationError {
        SLInstrumentTestSuite suite = c.getAnnotation(SLInstrumentTestSuite.class);
        if (suite == null) {
            throw new InitializationError(String.format("@%s annotation required on class '%s' to run with '%s'.", SLInstrumentTestSuite.class.getSimpleName(), c.getName(),
                            SLInstrumentTestRunner.class.getSimpleName()));
        }

        String[] paths = suite.value();

        Path root = null;
        for (String path : paths) {
            root = FileSystems.getDefault().getPath(path);
            if (Files.exists(root)) {
                break;
            }
        }
        if (root == null && paths.length > 0) {
            throw new FileNotFoundException(paths[0]);
        }

        final Path rootPath = root;

        final List<InstrumentTestCase> testCases = new ArrayList<>();

        // Scaffolding in place for future automation
        Files.walkFileTree(rootPath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path sourceFile, BasicFileAttributes attrs) throws IOException {
                String sourceName = sourceFile.getFileName().toString();
                if (sourceName.endsWith(SOURCE_SUFFIX)) {
                    String baseName = sourceName.substring(0, sourceName.length() - SOURCE_SUFFIX.length());

                    Path inputFile = sourceFile.resolveSibling(baseName + INPUT_SUFFIX);
                    String testInput = "";
                    if (Files.exists(inputFile)) {
                        testInput = readAllLines(inputFile);
                    }

                    Path outputFile = sourceFile.resolveSibling(baseName + OUTPUT_SUFFIX);
                    String expectedOutput = "";
                    if (Files.exists(outputFile)) {
                        expectedOutput = readAllLines(outputFile);
                    }

                    testCases.add(new InstrumentTestCase(c, baseName, sourceName, sourceFile, testInput, expectedOutput));

                }
                return FileVisitResult.CONTINUE;
            }
        });

        return testCases;
    }

    private static String readAllLines(Path file) throws IOException {
        // fix line feeds for non unix os
        StringBuilder outFile = new StringBuilder();
        for (String line : Files.readAllLines(file, Charset.defaultCharset())) {
            outFile.append(line).append(LF);
        }
        return outFile.toString();
    }

    /**
     * Executes the passed in test case. Instrumentation is added according to the name of the file
     * as explained in {@link #createTests(Class)}. Note that this code is not generalizable.
     */
    @Override
    protected void runChild(InstrumentTestCase testCase, RunNotifier notifier) {
        // TODO Current tests are hard-coded, automate this eventually
        notifier.fireTestStarted(testCase.name);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintWriter printer = new PrintWriter(out);
        final ASTProber prober = new SLStandardASTProber();
        Probe.registerASTProber(prober);
        try {
            // We use the name of the file to determine what visitor to attach to it.
            if (testCase.baseName.endsWith(ASSIGNMENT_VALUE_SUFFIX)) {
                // Set up the execution context for Simple and register our two listeners
                TruffleVM vm = TruffleVM.newVM().stdIn(new BufferedReader(new StringReader(testCase.testInput))).stdOut(printer).build();

                final String src = readAllLines(testCase.path);
                vm.eval("application/x-sl", src);

                // Attach an instrument to every probe tagged as an assignment
                for (Probe probe : Probe.findProbesTaggedAs(StandardSyntaxTag.ASSIGNMENT)) {
                    SLPrintAssigmentValueListener slPrintAssigmentValueListener = new SLPrintAssigmentValueListener(printer);
                    probe.attach(Instrument.create(slPrintAssigmentValueListener, "SL print assignment value"));
                }

                TruffleVM.Symbol main = vm.findGlobalSymbol("main");
                main.invoke(null);
            } else {
                notifier.fireTestFailure(new Failure(testCase.name, new UnsupportedOperationException("No instrumentation found.")));
            }

            printer.flush();
            String actualOutput = new String(out.toByteArray());
            Assert.assertEquals(testCase.expectedOutput, actualOutput);
        } catch (Throwable ex) {
            notifier.fireTestFailure(new Failure(testCase.name, ex));
        } finally {
            Probe.unregisterASTProber(prober);
            notifier.fireTestFinished(testCase.name);
        }

    }

    public static void runInMain(Class<?> testClass, String[] args) throws InitializationError, NoTestsRemainException {
        JUnitCore core = new JUnitCore();
        core.addListener(new TextListener(System.out));
        SLInstrumentTestRunner suite = new SLInstrumentTestRunner(testClass);
        if (args.length > 0) {
            suite.filter(new NameFilter(args[0]));
        }
        Result r = core.run(suite);
        if (!r.wasSuccessful()) {
            System.exit(1);
        }
    }

    private static final class NameFilter extends Filter {
        private final String pattern;

        private NameFilter(String pattern) {
            this.pattern = pattern.toLowerCase();
        }

        @Override
        public boolean shouldRun(Description description) {
            return description.getMethodName().toLowerCase().contains(pattern);
        }

        @Override
        public String describe() {
            return "Filter contains " + pattern;
        }
    }

    /**
     * This sample listener provides prints the value of an assignment (after the assignment is
     * complete) to the {@link PrintWriter} specified in the constructor. This listener can only be
     * attached at {@link SLWriteLocalVariableNode}, but provides no guards to protect it from being
     * attached elsewhere.
     */
    public final class SLPrintAssigmentValueListener extends DefaultSimpleInstrumentListener {

        private PrintWriter output;

        public SLPrintAssigmentValueListener(PrintWriter output) {
            this.output = output;
        }

        @Override
        public void returnValue(Probe probe, Object result) {
            output.println(result);
        }
    }

}