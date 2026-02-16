package com.tribixbite.stoatally.persistence

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.tribixbite.stoatally.StoatApplication

object SqlStorage {
    val driver: SqlDriver = AndroidSqliteDriver(
        Database.Schema,
        StoatApplication.instance.applicationContext,
        "revolt.db"
    )
}