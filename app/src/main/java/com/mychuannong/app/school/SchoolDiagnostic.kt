package com.mychuannong.app.school

/** Operational stages only: never include account IDs, school data, URLs or credentials. */
internal fun schoolDiagnostic(stage: String) {
    runCatching { android.util.Log.i("SchoolSession", stage) }
}
