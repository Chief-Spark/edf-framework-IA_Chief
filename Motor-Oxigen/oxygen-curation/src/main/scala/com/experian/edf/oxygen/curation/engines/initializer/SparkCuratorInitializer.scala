package  com.experian.edf.oxygen.curation.engines.initializer

import com.experian.edf.oxygen.context.{AppContext, ApplicationContext}
import com.experian.edf.oxygen.curation.engines.Curator

object SparkCuratorInitializer {

  def create(configLocations: Array[String], myParent: AppContext = null): Curator = {
    val appContext = ApplicationContext.create(configLocations, myParent, Array())
    Curator.create(appContext)
  }


}
