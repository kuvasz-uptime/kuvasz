package com.kuvaszuptime.kuvasz

import com.kuvaszuptime.kuvasz.jooq.Kuvasz
import org.jooq.DSLContext

fun DSLContext.resetDatabase() {
    Kuvasz.KUVASZ.tables.forEach { table ->
        this.deleteFrom(table).execute()
    }
}
