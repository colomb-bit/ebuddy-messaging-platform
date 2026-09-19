package com.ebuddy.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ActivityTestRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule val activity = ActivityTestRule(MainActivity::class.java)
    @Test fun launches(){ activity.activity }
}
