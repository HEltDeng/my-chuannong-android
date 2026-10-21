package com.mychuannong.app.school

/** Names, IDs and coordinates come verbatim from AMap, never from the illustration. */
internal object CampusAmapCatalogue {
    fun places(campus: CampusMap, data: CampusGeoData): List<CampusPlace> = data.pois
        .filter { it.id != data.center.id && valid(it) }
        .distinctBy { it.id }
        .mapNotNull { poi ->
            val category = category(poi) ?: return@mapNotNull null
            CampusPlace("amap-${campus.id}-${poi.id}", campus, poi.name, poi.name, category,
                searchAliases(poi.name, category), poi)
        }.sortedWith(compareBy<CampusPlace> { it.category.ordinal }.thenBy { it.name })

    fun valid(poi: CampusGeoPoi) = poi.id.isNotBlank() && poi.name.isNotBlank() &&
        poi.latitude.isFinite() && poi.longitude.isFinite() &&
        poi.latitude in 18.0..54.0 && poi.longitude in 73.0..135.0

    fun belongs(poi: CampusGeoPoi, campus: CampusMap, center: CampusGeoPoi, childOfCampus: Boolean = false): Boolean {
        val source = poi.name + poi.address
        if(!poi.name.contains("四川农业大学") && Regex("近四川农业大学|四川农业大学.*(附近|对面|旁边)").containsMatchIn(poi.address)) return false
        val schoolAddress=center.address.isNotBlank() && poi.address.contains(center.address)
        return valid(poi) && (childOfCampus || schoolAddress || source.contains("四川农业大学") || source.contains("四川农大") || source.contains("川农学生")) &&
            CampusMap.entries.none { it != campus && source.contains(it.title) } &&
            poi.distanceTo(center) < if(campus == CampusMap.YAAN) 1800 else 1200
    }

    fun category(poi: CampusGeoPoi): PlaceCategory? {
        val name = poi.name
        if(poi.typeCode.split('|').any { it.startsWith("10") }) return null // Lodging businesses, not student dormitories.
        // A university address alone does not make a shop, bus stop or car park a campus building.
        if(Regex("停车|充电|公交|车站|外卖|便利店|超市|咖啡|奶茶|香肠|快餐|酒店|宾馆|旅馆|民宿|营业厅|自动售|快递柜|取餐柜|ATM|自助银行", RegexOption.IGNORE_CASE).containsMatchIn(name)) return null
        return when {
            name.contains("图书馆") -> PlaceCategory.LIBRARY
            Regex("食堂|餐厅|膳食").containsMatchIn(name) -> PlaceCategory.DINING
            Regex("体育馆|体育场|运动场|操场|球场|球馆|游泳|健身房|训练室").containsMatchIn(name) -> PlaceCategory.SPORTS
            Regex("管理中心|办公楼").containsMatchIn(name) -> PlaceCategory.SERVICE
            Regex("公寓|宿舍|学生.*舍|博士生楼|教职工住宅").containsMatchIn(name) -> PlaceCategory.DORM
            Regex("医院|医务|卫生所").containsMatchIn(name) -> PlaceCategory.SERVICE
            Regex("校门|[东西南北正侧]门|出入口").containsMatchIn(name) -> PlaceCategory.GATE
            Regex("教学楼|教学实验|[0-9一二三四五六七八九十]教(?!学区)|学院|实验|科研|科学楼|理学楼|机电楼|院士楼|林园楼|报告厅|文法楼|作物楼|学术|教学部|体育系|试验站").containsMatchIn(name) -> PlaceCategory.TEACHING
            Regex("中心|后勤|快递站|驿站|展览馆|麦立方|学生会|团委|广场|荷花池|杨柳湖").containsMatchIn(name) -> PlaceCategory.SERVICE
            // Other named buildings are retained, without inventing their purpose.
            Regex("楼([A-Za-z区座栋0-9()（）-]*)$").containsMatchIn(name) -> PlaceCategory.SERVICE
            else -> null
        }
    }

    private fun searchAliases(name: String, category: PlaceCategory): List<String> {
        val normalized = CampusPlaces.normalize(name)
        val numbers = when(category) {
            PlaceCategory.TEACHING -> Regex("(?:第)?([0-9]+)(?:号)?(?:综合)?教(?!学区)").findAll(normalized)
            PlaceCategory.DORM -> (Regex("(?:第)?([0-9]+)(?:号)?(?:公寓|宿舍|舍|栋)").findAll(normalized) +
                Regex("(?:公寓|宿舍)(?:第)?([0-9]+)(?:号)?").findAll(normalized))
            else -> emptySequence()
        }
        // Aliases affect search only; every displayed title remains the vendor's full original title.
        return numbers.flatMap { match ->
            val n = match.groupValues[1]
            if(category == PlaceCategory.TEACHING) sequenceOf("${n}号", "${n}教", "${n}号教学楼")
            else sequenceOf("${n}号", "${n}舍", "${n}栋", "${n}号宿舍")
        }.toList()
    }
}
