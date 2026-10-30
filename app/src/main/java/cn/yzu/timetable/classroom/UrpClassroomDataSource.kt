package cn.yzu.timetable.classroom

import cn.yzu.timetable.data.urp.UrpConfig
import cn.yzu.timetable.data.urp.UrpHttpDataSource
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class CampusOption(val number: String, val name: String)
data class BuildingOption(val number: String, val name: String, val remark: String)
data class FreeRoom(val name: String, val seats: String, val floor: String)
data class FreeBuilding(val number: String, val name: String, val rooms: List<FreeRoom>)
data class FreeRoomResult(val campusName: String, val buildings: List<FreeBuilding>)

class ClassroomException(message: String) : Exception(message)

/**
 * 空闲教室查询（协议来自用户抓包）：
 * 1. GET  freeClassroom/index 页面内联 var xqh / var xqList 提供校区列表；
 * 2. GET  freeClassroom/queryCodeTeaBuildingList?&xqh=校区号 取教学楼；
 * 3. POST freeClassroom/today  form: position=校区号_楼号 & xqm=校区名 选定楼栋（会话态）；
 * 4. GET  freeClassroom/today/1,2,3?dayplus=0|1|2 取该楼在节次范围内的空闲教室。
 * 复用登录会话 Cookie（只读传入），不保存任何凭据。
 */
class UrpClassroomDataSource(private val cookieProvider: () -> String?) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
    private val base = UrpConfig().baseUrl
    private val prefix = "$base/student/teachingResources/freeClassroom"

    private fun Request.Builder.withCookie(): Request.Builder {
        val cookie = cookieProvider() ?: throw ClassroomException("教务会话已失效，请在“我的”中重新登录后再查询")
        return header("Cookie", cookie).header("User-Agent", UrpHttpDataSource.USER_AGENT)
    }

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val call = client.newCall(Request.Builder().url(url).withCookie().build())
        call.execute().use { r ->
            if (!r.isSuccessful) throw ClassroomException("教务服务返回 ${r.code}，请稍后重试")
            r.body?.string().orEmpty()
        }
    }

    suspend fun campuses(): List<CampusOption> = parseCampuses(get("$prefix/index"))

    suspend fun buildings(campusNumber: String): List<BuildingOption> = parseBuildings(get("$prefix/queryCodeTeaBuildingList?&xqh=$campusNumber"))

    suspend fun selectBuilding(campus: CampusOption, building: BuildingOption) {
        val body = FormBody.Builder().add("position", campus.number + "_" + building.number).add("xqm", campus.name).build()
        val request = Request.Builder().url("$prefix/today").withCookie().post(body).build()
        withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { r ->
                if (!r.isSuccessful) throw ClassroomException("选择教学楼失败（${r.code}）")
            }
        }
    }

    suspend fun freeRooms(periods: List<Int>, dayplus: Int): FreeRoomResult =
        parseFreeRooms(get("$prefix/today/" + periods.joinToString(",") + "?dayplus=$dayplus"))

    companion object {
        internal fun parseCampuses(html: String): List<CampusOption> {
            val default = Regex("""var\s+xqh\s*=\s*'(\d+)'""").find(html)?.groupValues?.getOrNull(1)
            val list = Regex("""var\s+xqList\s*=\s*'(\[.*?\])'""", RegexOption.DOT_MATCHES_ALL).find(html)?.groupValues?.getOrNull(1)
                ?: throw ClassroomException("未能解析校区列表")
            val arr = JsonParser.parseString(list).asJsonArray
            val options = arr.map { o -> CampusOption(o.asJsonObject.get("campusNumber").asString, o.asJsonObject.get("campusName").asString) }
            return if (default != null) options.sortedByDescending { it.number == default } else options
        }

        internal fun parseBuildings(json: String): List<BuildingOption> {
            val arr = JsonParser.parseString(json).asJsonArray
            return arr.map { o ->
                val id = o.asJsonObject.getAsJsonObject("id")
                BuildingOption(id.get("teachingBuildingNumber").asString, o.asJsonObject.get("teachingBuildingName").asString,
                    o.asJsonObject.get("remark")?.asString.orEmpty())
            }.filter { it.remark.isBlank() }
        }

        internal fun parseFreeRooms(json: String): FreeRoomResult {
            val obj = JsonParser.parseString(json).asJsonObject
            val campusName = obj.get("curxqm")?.asString.orEmpty()
            val buildings = obj.getAsJsonArray("spareroomObjList")?.map { b ->
                val bo = b.asJsonObject
                FreeBuilding(bo.get("acmcBuilding").asString, bo.get("acmcBuildingName").asString,
                    bo.getAsJsonArray("claroom")?.map { r ->
                        val ro = r.asJsonObject
                        FreeRoom(ro.get("classroom").asString, ro.get("classNumberOfSeats")?.asString.orEmpty(), ro.get("szlc")?.asString.orEmpty())
                    }.orEmpty())
            }.orEmpty()
            return FreeRoomResult(campusName, buildings)
        }
    }
}
