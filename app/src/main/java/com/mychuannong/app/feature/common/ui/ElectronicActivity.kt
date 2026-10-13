package com.mychuannong.app.feature.common.ui

import com.mychuannong.app.core.web.ServiceWebActivity

/** Device selection, roommates, rooms, meters and payments belong to the provider. */
class ElectronicActivity : ServiceWebActivity() {
    override val serviceTitle = "电费查询"
    override val initialUrl = "http://bd.bdfairy.cn/selectservice"
    override val supportsPayment = true

}
