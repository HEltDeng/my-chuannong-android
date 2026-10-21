package com.mychuannong.app.school

object LearningCacheStatus {
    fun text(cached:Boolean,updatedAt:Long):String {
        val time=if(updatedAt>0)java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",java.util.Locale.CHINA).format(java.util.Date(updatedAt)) else "未记录（旧版缓存）"
        return (if(cached)"当前为本地缓存数据" else "当前为最新获取的数据")+" · 最后更新时间："+time
    }
}
