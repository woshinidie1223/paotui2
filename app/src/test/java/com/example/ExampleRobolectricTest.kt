package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Robolectric

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("速达跑腿", appName)
  }

  @Test
  fun `launch main activity`() {
    try {
      val controller = Robolectric.buildActivity(MainActivity::class.java)
      controller.setup()
      val activity = controller.get()
      System.out.println("MainActivity launched successfully inside Robolectric test. Activity non-null: " + (activity != null))
    } catch (e: Throwable) {
      System.err.println("CRASH CAPTURED IN ROBOLECTRIC:")
      e.printStackTrace()
      throw e
    }
  }
}
