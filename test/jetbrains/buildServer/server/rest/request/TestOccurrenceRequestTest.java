/*
 * Copyright 2000-2019 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.request;

import jetbrains.buildServer.buildTriggers.vcs.BuildBuilder;
import jetbrains.buildServer.server.rest.data.BaseFinderTest;
import jetbrains.buildServer.server.rest.model.problem.TestOccurrence;
import jetbrains.buildServer.server.rest.model.problem.TestOccurrences;
import jetbrains.buildServer.serverSide.SFinishedBuild;
import jetbrains.buildServer.serverSide.STestRun;
import jetbrains.buildServer.serverSide.impl.BuildTypeImpl;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * @author Yegor.Yarko
 * Date: 26/03/2019
 */
public class TestOccurrenceRequestTest extends BaseFinderTest<STestRun> {
  private TestOccurrenceRequest myRequest;

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    myRequest = new TestOccurrenceRequest();
    myRequest.initForTests(BaseFinderTest.getBeanContext(myFixture));
  }

  @Test
  public void testTestOccurrenceFields () {
    final BuildTypeImpl buildType = registerBuildType("buildConf1", "project");
    final SFinishedBuild build10 = build().in(buildType)
                                          .withTest(BuildBuilder.TestData.test("aaa").duration(76))
                                          .withTest(BuildBuilder.TestData.test("bbb").out("std out").errorOut("str err")
                                                                         .failed("error message", "stacktrace\nline 1\r\nline2").duration(67))
                                          .finish();
    {
      TestOccurrences testOccurrences = myRequest.getTestOccurrences("build:(id:" + build10.getBuildId() + "),status:FAILURE", "**", null, null);

      assertEquals(Integer.valueOf(1), testOccurrences.count);
      assertEquals(1, testOccurrences.items.size());
      TestOccurrence testOccurrence = testOccurrences.items.get(0);
      assertEquals("bbb", testOccurrence.name);
      assertEquals("2", testOccurrence.runOrder);
      assertEquals(Integer.valueOf(67), testOccurrence.duration);
      assertEquals("FAILURE", testOccurrence.status);
      assertEquals(Boolean.valueOf(false), testOccurrence.ignored);
      assertNull(testOccurrence.ignoreDetails);
      assertEquals("error message\nstacktrace\nline 1\r\nline2\n------- Stdout: -------\nstd out\n------- Stderr: -------\nstr err", testOccurrence.details);
    }


    final SFinishedBuild build20 = build().in(buildType)
                                          .withTest(BuildBuilder.TestData.test("aaa").duration(76))
                                          .withTest(BuildBuilder.TestData.test("bbb").failed("error message", "stacktrace\nline 1\nline2").duration(67))
                                          .withTest(BuildBuilder.TestData.test("ccc").ignored("Ignore reason").out("std\r\nout").duration(67))
                                          .finish();
    {
      TestOccurrences testOccurrences = myRequest.getTestOccurrences("build:(id:" + build20.getBuildId() + "),ignored:true", "**", null, null);

      assertEquals(Integer.valueOf(1), testOccurrences.count);
      assertEquals(1, testOccurrences.items.size());
      TestOccurrence testOccurrence = testOccurrences.items.get(0);
      assertEquals("ccc", testOccurrence.name);
      assertEquals("3", testOccurrence.runOrder);
      assertEquals(Integer.valueOf(0), testOccurrence.duration);
      assertEquals("UNKNOWN", testOccurrence.status);
      assertEquals(Boolean.valueOf(true), testOccurrence.ignored);
      assertEquals("Ignore reason", testOccurrence.ignoreDetails);
      assertNull(testOccurrence.details);
    }

    //checking how ignored and failed test looks like. Just asserting current behavior
    final SFinishedBuild build30 = build().in(buildType)
                                          .withTest(BuildBuilder.TestData.test("aaa").duration(76))
                                          .withTest(BuildBuilder.TestData.test("bbb").failed("error message", "stacktrace\nline 1\nline2").duration(67))
                                          .withTest(BuildBuilder.TestData.test("ccc").ignored("Ignore reason").failed("error message", "stacktrace\nline 1\nline2").duration(67))
                                          .finish();
    {
      TestOccurrences testOccurrences = myRequest.getTestOccurrences("build:(id:" + build30.getBuildId() + "),test:(name:ccc)", "**", null, null);

      assertEquals(Integer.valueOf(1), testOccurrences.count);
      assertEquals(1, testOccurrences.items.size());
      TestOccurrence testOccurrence = testOccurrences.items.get(0);
      assertEquals("ccc", testOccurrence.name);
      assertEquals("3", testOccurrence.runOrder);
      assertEquals(Integer.valueOf(0), testOccurrence.duration);
      assertEquals("FAILURE", testOccurrence.status);
      assertEquals(Boolean.valueOf(false), testOccurrence.ignored);
      assertEquals(null, testOccurrence.ignoreDetails);
      assertEquals("error message\nstacktrace\nline 1\nline2", testOccurrence.details);
    }
  }
}