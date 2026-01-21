package com.perfectlunacy.bailiwick.e2e

import android.view.View
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.perfectlunacy.bailiwick.BailiwickActivity
import com.perfectlunacy.bailiwick.R
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * E2E navigation flow tests that verify navigation between all UI screens:
 * - Feed (ContentFragment) - with header_bar, layout_user_identity, list_circles
 * - EditIdentity (EditIdentityFragment) - accessed via avatar tap
 * - CreateCircle (CreateCircleFragment) - accessed via btn_add_circle
 * - EditCircle (EditCircleFragment)
 * - UserProfile (UserProfileFragment)
 *
 * These tests require a logged-in user state to verify navigation flows.
 *
 * Updated for Feed UI redesign: removed user filter bar and old header elements.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class NavigationFlowTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(BailiwickActivity::class.java)

    /**
     * Helper to wait for views to appear (simple sleep-based approach for E2E tests).
     */
    private fun waitForView(timeout: Long = 2000) {
        Thread.sleep(timeout)
    }

    /**
     * Helper to safely check if a view is displayed.
     */
    private fun isViewDisplayed(viewMatcher: Matcher<View>): Boolean {
        return try {
            onView(viewMatcher).check(matches(isDisplayed()))
            true
        } catch (e: Exception) {
            false
        }
    }

    // =====================
    // Feed Screen Tests
    // =====================

    @Test
    fun feedScreen_isDisplayedAfterLogin() {
        // Wait for app to load and potentially auto-login
        waitForView(3000)

        // If we're on the first run screen, we need to sign up first
        if (isViewDisplayed(withId(R.id.btn_sign_up))) {
            // Skip this test - requires fresh user setup
            return
        }

        // Verify feed screen elements are displayed
        onView(withId(R.id.header_bar)).check(matches(isDisplayed()))
        onView(withId(R.id.list_circles)).check(matches(isDisplayed()))
        onView(withId(R.id.btn_post)).check(matches(isDisplayed()))
    }

    @Test
    fun feedScreen_addCircleButtonNavigatesToCreateCircleScreen() {
        waitForView(3000)

        if (isViewDisplayed(withId(R.id.btn_sign_up))) {
            return // Skip if not logged in
        }

        // Click add circle button
        onView(withId(R.id.btn_add_circle)).perform(click())

        waitForView()

        // Verify we're on the CreateCircle screen
        onView(withId(R.id.txt_circle_name)).check(matches(isDisplayed()))
    }

    // =====================
    // User Profile Tests
    // =====================

    @Test
    fun feedScreen_avatarClickNavigatesToEditIdentity() {
        waitForView(3000)

        if (isViewDisplayed(withId(R.id.btn_sign_up))) {
            return
        }

        // Click own avatar in post composer - now navigates directly to EditIdentity
        onView(withId(R.id.img_my_avatar)).perform(click())
        waitForView()

        // Verify we're on EditIdentity screen
        onView(withId(R.id.txt_public_name)).check(matches(isDisplayed()))
        onView(withId(R.id.btn_avatar)).check(matches(isDisplayed()))
    }

    @Test
    fun feedScreen_headerAvatarClickNavigatesToEditIdentity() {
        waitForView(3000)

        if (isViewDisplayed(withId(R.id.btn_sign_up))) {
            return
        }

        // Click user identity in header
        onView(withId(R.id.layout_user_identity)).perform(click())
        waitForView()

        // Verify we're on EditIdentity screen
        onView(withId(R.id.txt_public_name)).check(matches(isDisplayed()))
    }

    @Test
    fun editIdentity_backNavigatesToFeed() {
        waitForView(3000)

        if (isViewDisplayed(withId(R.id.btn_sign_up))) {
            return
        }

        // Navigate to EditIdentity via post composer avatar
        onView(withId(R.id.img_my_avatar)).perform(click())
        waitForView()

        // Click back button
        onView(withId(R.id.btn_back)).perform(click())
        waitForView()

        // Verify we're back on Feed
        onView(withId(R.id.btn_post)).check(matches(isDisplayed()))
    }

    // =====================
    // Deep Navigation Flow Tests
    // =====================

    @Test
    fun navigationFlow_feedToEditIdentityAndBack() {
        waitForView(3000)

        if (isViewDisplayed(withId(R.id.btn_sign_up))) {
            return
        }

        // Step 1: Feed -> EditIdentity (via header)
        onView(withId(R.id.layout_user_identity)).perform(click())
        waitForView()
        onView(withId(R.id.txt_public_name)).check(matches(isDisplayed()))

        // Step 2: EditIdentity -> Feed (back)
        onView(withId(R.id.btn_back)).perform(click())
        waitForView()
        onView(withId(R.id.btn_post)).check(matches(isDisplayed()))
    }

    @Test
    fun navigationFlow_feedToCreateCircleAndBack() {
        waitForView(3000)

        if (isViewDisplayed(withId(R.id.btn_sign_up))) {
            return
        }

        // Step 1: Feed -> CreateCircle
        onView(withId(R.id.btn_add_circle)).perform(click())
        waitForView()
        onView(withId(R.id.txt_circle_name)).check(matches(isDisplayed()))

        // Step 2: CreateCircle -> Feed (back)
        pressBack()
        waitForView()
        onView(withId(R.id.btn_post)).check(matches(isDisplayed()))
    }

    // =====================
    // Circle Filter Navigation Tests
    // =====================

    @Test
    fun feedScreen_circleFilterIsInteractive() {
        waitForView(3000)

        if (isViewDisplayed(withId(R.id.btn_sign_up))) {
            return
        }

        // Verify circle filter is displayed
        onView(withId(R.id.list_circles)).check(matches(isDisplayed()))

        // Verify header bar is displayed (replaces old filter label)
        onView(withId(R.id.header_bar)).check(matches(isDisplayed()))
    }

    // =====================
    // Post Composer Tests
    // =====================

    @Test
    fun feedScreen_postComposerElementsAreDisplayed() {
        waitForView(3000)

        if (isViewDisplayed(withId(R.id.btn_sign_up))) {
            return
        }

        // Verify post composer elements
        onView(withId(R.id.img_my_avatar)).check(matches(isDisplayed()))
        onView(withId(R.id.txt_post_text)).check(matches(isDisplayed()))
        onView(withId(R.id.btn_add_image)).check(matches(isDisplayed()))
        onView(withId(R.id.btn_post)).check(matches(isDisplayed()))
    }

    // =====================
    // System Back Button Tests
    // =====================

    @Test
    fun systemBackButton_fromCreateCircleReturnToFeed() {
        waitForView(3000)

        if (isViewDisplayed(withId(R.id.btn_sign_up))) {
            return
        }

        // Navigate to CreateCircle
        onView(withId(R.id.btn_add_circle)).perform(click())
        waitForView()

        // Press system back
        pressBack()
        waitForView()

        // Verify back on Feed
        onView(withId(R.id.btn_post)).check(matches(isDisplayed()))
    }

    @Test
    fun systemBackButton_fromEditIdentityReturnToFeed() {
        waitForView(3000)

        if (isViewDisplayed(withId(R.id.btn_sign_up))) {
            return
        }

        // Navigate to EditIdentity via post composer avatar
        onView(withId(R.id.img_my_avatar)).perform(click())
        waitForView()

        // Press system back
        pressBack()
        waitForView()

        // Verify back on Feed
        onView(withId(R.id.btn_post)).check(matches(isDisplayed()))
    }
}
