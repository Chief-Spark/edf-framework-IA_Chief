package com.experian.edf.oxygen.pinning.util

import com.experian.edf.oxygen.domain.TaggedIdentity


abstract class ClusterSplitter extends Serializable {

  /**
    * Apply domain specific rules to split the cluster of matched identities.
    * @param cluster list of matched identities
    * @return list of list of identities after splitting
    */
  def splitCluster(cluster: List[TaggedIdentity], identityMatcher:  IdentityMatcher): List[List[TaggedIdentity]]
}
