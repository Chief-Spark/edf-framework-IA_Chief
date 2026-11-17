package com.experian.edf.oxygen.consistencycheck.standalone

object SynthesisConstants {

  private[oxygen] val INFERRED_DELETE_CHECKNAME = "OxygenInferredDelete"
  // Error Code
  private[oxygen] val INFERRED_DELETE_SYNTHESYS = "OXY-SYN-1"
  private[oxygen] val INFERRED_DELETE_SYNTHESYS_SUBCODE = "InferredDeleted"

  // Consistency
  private[oxygen] val kTrue = "true"
  private[oxygen] val OPEN = "open"
  private[oxygen] val CLOSE = "close"
  private[oxygen] val SHOULD_ACCEPT_STATE = "isStateOpen"
  private[oxygen] val IS_STATE_CHECK = "isStateCheck"
  private[oxygen] val STATENAME = "stateName"
  private[oxygen] val STATEID = "stateId"
  private[oxygen] val VOID_INDICATOR = "voidIndicator"
  private[oxygen] val kEnableRefCacheLikeNP = "enableRefCacheLikeNonPeriodsOnHF"
}
