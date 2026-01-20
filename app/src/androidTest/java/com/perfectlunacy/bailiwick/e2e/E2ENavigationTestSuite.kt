package com.perfectlunacy.bailiwick.e2e

import org.junit.runner.RunWith
import org.junit.runners.Suite

/**
 * Test suite for all E2E navigation flow tests.
 *
 * This suite runs all navigation tests across the app's UI screens:
 * - NavigationFlowTest: Basic navigation between main screens
 * - ContactCircleNavigationTest: Contact and circle management navigation
 *
 * Run this suite on physical devices for best results:
 *   ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.perfectlunacy.bailiwick.e2e.E2ENavigationTestSuite
 */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    NavigationFlowTest::class,
    ContactCircleNavigationTest::class
)
class E2ENavigationTestSuite
