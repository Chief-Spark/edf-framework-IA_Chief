package com.experian.edf.oxygen.consistencycheck.extension

import com.experian.edf.oxygen.domain.ConsistencyInfo
import com.experian.edf.oxygen.domain.internal.Period

class CreditLimitChangeInfo(override val code: String, override val subCode: String, override val level: String,
                                  val detail: String,
                                  val period: Period) extends ConsistencyInfo(code, subCode, level) {

}