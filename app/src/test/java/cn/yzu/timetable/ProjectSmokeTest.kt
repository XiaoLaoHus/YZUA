package cn.yzu.timetable

import org.junit.Assert.assertEquals
import org.junit.Test
class ProjectSmokeTest { @Test fun appIdIsStable() { assertEquals("cn.yzu.timetable", BuildInfo.appId) } }
