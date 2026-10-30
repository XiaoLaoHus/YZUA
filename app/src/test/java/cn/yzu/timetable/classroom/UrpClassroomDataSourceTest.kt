package cn.yzu.timetable.classroom

import org.junit.Assert.assertEquals
import org.junit.Test

class UrpClassroomDataSourceTest {
    @Test fun parsesCampusListFromInlineScript() {
        val html = "<script>var xqh = '08';var xqList = '[{\"campusName\":\"扬子津东校区\",\"campusNumber\":\"08\"},{\"campusName\":\"瘦西湖校区\",\"campusNumber\":\"10\"}]';</script>"
        val list = UrpClassroomDataSource.parseCampuses(html)
        assertEquals("08", list.first().number)
        assertEquals(2, list.size)
    }

    @Test fun parsesBuildingsAndFiltersRemarks() {
        val json = """[{"id":{"campusNumber":"08","teachingBuildingNumber":"801"},"remark":"","teachingBuildingName":"文津楼"},
            {"id":{"campusNumber":"08","teachingBuildingNumber":"0802"},"remark":"停用","teachingBuildingName":"东操场"}]"""
        val list = UrpClassroomDataSource.parseBuildings(json)
        assertEquals(1, list.size)
        assertEquals("文津楼", list.first().name)
    }

    @Test fun parsesFreeRooms() {
        val json = """{"curxqm":"扬子津东校区","spareroomObjList":[{"acmcBuilding":"801","acmcBuildingName":"文津楼",
            "claroom":[{"classNumberOfSeats":"70","classroom":"S407","szlc":""},{"classNumberOfSeats":"93","classroom":"410","szlc":"4"}]}]}"""
        val r = UrpClassroomDataSource.parseFreeRooms(json)
        assertEquals("扬子津东校区", r.campusName)
        assertEquals(1, r.buildings.size)
        assertEquals(listOf("S407", "410"), r.buildings.first().rooms.map { it.name })
        assertEquals("70", r.buildings.first().rooms.first().seats)
    }
}
