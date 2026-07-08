package com.kakeibo.android.core.util

import kotlin.math.abs

/** Yen formatting helpers mirroring the web's `toLocaleString('ja-JP')` grouping. */

fun formatYen(amount: Long): String = "¥" + "%,d".format(abs(amount))

/** Signed by transaction type: income → `+¥`, expense → `−¥`. */
fun formatYenSigned(type: String, amount: Long): String {
    val sign = if (type == "income") "+" else "−"
    return "$sign¥" + "%,d".format(abs(amount))
}

/** Signed by the sum's own sign (negative → `−¥`). */
fun formatYenSignedSum(sum: Long): String {
    val sign = if (sum < 0) "−" else "+"
    return "$sign¥" + "%,d".format(abs(sum))
}
