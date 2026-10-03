package com.mychuannong.app.school
import com.google.gson.Gson
import com.tencent.mmkv.MMKV

/** One atomic verified bundle; test stores must use a separate namespace. */
class VerifiedAcademicCache(private val id:String="sicau_public_academic_v1") {
    private val store get()=MMKV.mmkvWithID(id)
    fun load(fallback:PublicAcademicData):PublicAcademicData = runCatching{SicauAcademicData.parse(store.decodeString("verified").orEmpty())}.getOrDefault(fallback)
    fun commit(candidate:PublicAcademicData):PublicAcademicData {
        val raw=Gson().toJson(candidate)
        val valid=SicauAcademicData.parse(raw)
        store.decodeString("verified")?.let{old->if(runCatching{SicauAcademicData.parse(old).version}.getOrNull()!=valid.version)store.encode("previous_verified",old)}
        check(store.encode("verified",raw)){"本地缓存保存失败"}
        return valid
    }
}
