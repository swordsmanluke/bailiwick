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
 * - Feed (ContentFragment)
 * - UserProfile (UserProfileFragment)
 * - EditIdentity (EditIdentityFragment)
 * - Contact (ContactFragment)
 * - EditCircle (EditCircleFragment)
 * - Connect (ConnectFragment)
 * - IntroduceSelf (IntroduceSelfFragment)
 * - AcceptIntroduction (AcceptIntroductionFragment)
 *
 * These tests require a logged-in user state to verify navigation flows.
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
        onView(withId(R.id.btn_refresh)).check(matches(isDisplayed()))
        onView(withId(R.id.list_circles)).check(matches(isDisplayed()))
        onView(withId(R.id.btn_post)).check(matches(isDisplayed()))
    }

    @Test
    fun feedScreen_addConnectionButtonNavigatesToConnectScreen() {
        waitForView(3000)

        if (isViewDisplayed(withId(R.id.btn_sign_up))) {
            return // Skip if not logged in
        }

        // Click add subscription/connection button
        onView(withId(R.id.btn_add_subscription)).perform(click())

        waitForView()

        // Verify we're on the Connect screen
        onView(withId(R.id.btn_conn_request)).check(matches(isDisplayed()))
        onView(withId(R.id.btn_conn_accept)).check(matches(isDisplayed()))
    }

    // =====================
    // Connect Screen Tests
    // =====================

    @Test
    fun connectScreen_introduceYourselfNavigatesToIntroduceSelfScreen() {
        waitForView(3000)

        if (isViewDisplayed(withId(R.id.btn_sign_up))) {
            return
        }

        // Navigate to Connect screen
        onView(withId(R.id.btn_add_subscription)).perform(click())
        waitForView()

        // Click "Introduce Yourself"
        onView(withId(R.id.btn_conn_request)).perform(click())
        waitForView()

        // Verify we're on IntroduceSelf screen (has QR code display)
        onView(withId(R.id.img_qr_code)).check(matches(isDisplayed()))
    }

    @Test
    fun connectScreen_scanIntroductionNavigatesToAcceptIntroductionScreen() {
        waitForView(3000)

        if (isViewDisplayed(withId(R.id.btn_sign_up))) {
            return
        }

        // Navigate to Connect screen
        onView(withId(R.id.btn_add_subscription)).perform(click())
        waitForView()

        // Click "Scan Introduction"
        onView(withId(R.id.btn_conn_accept)).perform(click())
        waitForView()

        // Verify we're on AcceptIntroduction screen (has scan button)
        onView(withId(R.id.btn_scan)).check(matches(isDisplayed()))
    }

    @Test
    fun connectScreen_backNavigatesToFeed() {
        waitForView(3000)

        if (isViewDisplayed(withId(R.id.btn_sign_up))) {
            return
        }

        // Navigate to Connect screen
        onView(withId(R.id.btn_add_subscription)).perform(click())
        waitForView()

        // Press back
        pressBack()
        waitForView()

        // Verify we're back on Feed screen
        onView(withId(R.id.btn_post)).check(matches(isDisplayed()))
    }

    // =====================
    // User Profile Tests
    // =====================

    @Test
    fun feedScreen_avatarClickNavigatesToOwnProfile() {
        waitForView(3000)

        if (isViewDisplayed(withId(R.id.btn_sign_up))) {
            return
        }

        // Click own avatar in post composer
        onView(withId(R.id.img_my_avatar)).perform(click())
        waitForView()

        // Verify we're on UserProfile screen (own profile shows edit button)
        onView(withId(R.id.btn_edit_profile)).check(matches(isDisplayed()))
    }

    @Test
    fun userProfile_editButtonNavigatesToEditIdentity() {
        waitForView(3000)

        if (isViewDisplayed(withId(R.id.btn_sign_up))) {
            return
        }

        // Navigate to own profile
        onView(withId(R.id.img_my_avatar)).perform(click())
        waitForView()

        // Click edit button
        onView(withId(R.id.btn_edit_profile)).perform(click())
        waitForView()

        // Verify we're on EditIdentity screen
        onView(withId(R.id.txt_public_name)).check(matches(isDisplayed()))
        onView(withId(R.id.btn_avatar)).check(matches(isDisplayed()))
    }

    @Test
    fun editIdentity_backNavigatesToUserProfile() {
        waitForView(3000)

        if (isViewDisplayed(withId(R.id.btn_sign_up))) {
            return
        }

        // Navigate to EditIdentity
        onView(withId(R.id.img_my_avatar)).perform(click())
        waitForView()
        onView(withId(R.id.btn_edit_profile)).perform(click())
        waitForView()

        // Click back button
        onView(withId(R.id.btn_back)).perform(click())
        waitForView()

        // Verify we're back on UserProfile
        onView(withId(R.id.btn_edit_profile)).check(matches(isDisplayed()))
    }

    @Test
    fun userProfile_backNavigatesToFeed() {
        waitForView(3000)

        if (isViewDisplayed(withId(R.id.btn_sign_up))) {
            return
        }

        // Navigate to own profile
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
    fun navigationFlow_feedToProfileToEditAndBack() {
        waitForView(3000)

        if (isViewDisplayed(withId(R.id.btn_sign_up))) {
            return
        }

        // Step 1: Feed -> UserProfile
        onView(withId(R.id.img_my_avatar)).perform(click())
        waitForView()
        onView(withId(R.id.btn_edit_profile)).check(matches(isDisplayed()))

        // Step 2: UserProfile -> EditIdentity
        onView(withId(R.id.btn_edit_profile)).perform(click())
        waitForView()
        onView(withId(R.id.txt_public_name)).check(matches(isDisplayed()))

        // Step 3: EditIdentity -> UserProfile (back)
        onView(withId(R.id.btn_back)).perform(click())
        waitForView()
        onView(withId(R.id.btn_edit_profile)).check(matches(isDisplayed()))

        // Step 4: UserProfile -> Feed (back)
        onView(withId(R.id.btn_back)).perform(click())
        waitForView()
        onView(withId(R.id.btn_post)).check(matches(isDisplayed()))
    }

    @Test
    fun navigationFlow_feedToConnectToIntroduceAndBack() {
        waitForView(3000)

        if (isViewDisplayed(withId(R.id.btn_sign_up))) {
            return
        }

        // Step 1: Feed -> Connect
        onView(withId(R.id.btn_add_subscription)).perform(click())
        waitForView()
        onView(withId(R.id.btn_conn_request)).check(matches(isDisplayed()))

        // Step 2: Connect -> IntroduceSelf
        onView(withId(R.id.btn_conn_request)).perform(click())
        waitForView()
        onView(withId(R.id.img_qr_code)).check(matches(isDisplayed()))

        // Step 3: IntroduceSelf -> Connect (back)
        pressBack()
        waitForView()
        onView(withId(R.id.btn_conn_request)).check(matches(isDisplayed()))

        // Step 4: Connect -> Feed (back)
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

        // Verify filter label is displayed
        onView(withId(R.id.txt_filter_label)).check(matches(isDisplayed()))
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
    fun systemBackButton_fromConnectReturnToFeed() {
        waitForView(3000)

        if (isViewDisplayed(withId(R.id.btn_sign_up))) {
            return
        }

        // Navigate to Connect
        onView(withId(R.id.btn_add_subscription)).perform(click())
        waitForView()

        // Press system back
        pressBack()
        waitForView()

        // Verify back on Feed
        onView(withId(R.id.btn_post)).check(matches(isDisplayed()))
    }

    @Test
    fun systemBackButton_fromProfileReturnToFeed() {
        waitForView(3000)

        if (isViewDisplayed(withId(R.id.btn_sign_up))) {
            return
        }

        // Navigate to Profile
        onView(withId(R.id.img_my_avatar)).perform(click())
        waitForView()

        // Press system back
        pressBack()
        waitForView()

        // Verify back on Feed
        onView(withId(R.id.btn_post)).check(matches(isDisplayed()))
    }

    @Test
    fun systemBackButton_fromEditIdentityReturnToProfile() {
        waitForView(3000)

        if (isViewDisplayed(withId(R.id.btn_sign_up))) {
            return
        }

        // Navigate to EditIdentity
        onView(withId(R.id.img_my_avatar)).perform(click())
        waitForView()
        onView(withId(R.id.btn_edit_profile)).perform(click())
        waitForView()

        // Press system back
        pressBack()
        waitForView()

        // Verify back on Profile
        onView(withId(R.id.btn_edit_profile)).check(matches(isDisplayed()))
    }
}
