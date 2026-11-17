package com.experian.edf.oxygen.pinning.standalone.classifier

import com.experian.edf.oxygen.domain.vle.VleMetadataFile

import java.io.File

/**
 *
 * @author Abraham, Thomas - C15627A
 *         Created on 8/20/20 at 12:26 PM
 */
case class ClassifierConfig(
                             celebrityFile: VleMetadataFile,
                             workingDirectory: String,
                             generation: String = "default"
                           ) {
  def generationDirectory: String = workingDirectory + File.separator + generation

  def classificationDirectory: String = generationDirectory + File.separator + "classified"

  def extractedDirectory: String = generationDirectory + File.separator + "extracted"

}
