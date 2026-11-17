package com.experian.edf.oxygen.acceptance.util

import java.io._
import java.nio.charset.StandardCharsets
import java.security.AccessControlException
import java.util.regex.Pattern
import com.experian.edf.oxygen.base.Constants
import com.experian.edf.oxygen.context.{AppContext, OxygenAppContext, SparkOxygenAppContext}
import com.experian.edf.oxygen.global.processmngt.ProcessUpdate
import com.experian.edf.oxygen.utils.{FileUtils, JsonUtils, LocalFileUtils}
import com.experian.edf.oxygen.utils.ObjectUtils._
import org.apache.commons.lang.StringEscapeUtils.escapeJava
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.permission.FsAction
import org.apache.hadoop.fs.{FSDataOutputStream, FileStatus, FileSystem, FileUtil, Path, PathFilter}
import org.apache.hadoop.io.IOUtils

import scala.collection.mutable.ListBuffer
import scala.util.Try

class SparkFileUtils(appContext: SparkOxygenAppContext) extends FileUtils(appContext) {

  @transient lazy val sc = appContext.sparkSession.sparkContext

  /**
   *
   * Returns the names of files (URL). It filters out the names of the directory
   *
   * @param dir
   * @param recursive
   * @return
   *
   */
  override def listFilenames(dir: String, recursive: Boolean = true): List[String] = {
    val hadoopConf = sc.hadoopConfiguration
    val path = new Path(dir)
    val fs = FileSystem.get(path.toUri, hadoopConf)

    try {
      val statusIter = fs.listFiles(path, recursive)
      val filenames = {
        val filenameBuffer = new ListBuffer[String]
        while (statusIter.hasNext) {
          val status = statusIter.next
          //          logger.debug("what is each getpath " + status.getPath + "getName " + status.getPath.getName + " getParent" + status.getPath.getParent)
          //          if ((!status.isDirectory) && (status.getPath.getName.contains("_SUCCESS") == false)) filenameBuffer += status.getPath.toString
          if ((!status.isDirectory) && (!status.getPath.getName.contains("_SUCCESS")) && (!status.getPath().getParent().toString.contains("_spark_metadata")))
            filenameBuffer += status.getPath.toString
        }
        filenameBuffer.toList
      }
      filenames
    }
    catch {
      // If we dont find the directory
      case fnfe: FileNotFoundException => {
        logger.error(fnfe.getMessage)
        List("")
      }
    }
    finally {
      fs.close()
    }

  }

  /**
   * List the directories with or without recursion
   *
   * @param dir
   * @param recursive
   * @return
   */
  override def listDirectories(dir: String, recursive: Boolean): List[String] = {

    val hadoopConf = sc.hadoopConfiguration
    val dirPath = new Path(dir)
    val filesystem = FileSystem.get(dirPath.toUri, hadoopConf)

    // List the sub directories under this directory. If there is a list recurse each one. If not, you have found
    // what you need. Add it to the list

    val accum = new ListBuffer[String]

    def isLastSubDirectory(dirPath: Path): Boolean = {
      if (filesystem.listStatus(dirPath).isEmpty)
        true
      else if (filesystem.listStatus(dirPath).filter(_.isDirectory).isEmpty)
        true
      else
        false
    }

    def getAllLeafNodeDirectories(dirPath: Path): Unit = {
      if (isLastSubDirectory(dirPath))
        accum.append(dirPath.toString)
      else {
        filesystem.listStatus(dirPath).filter(_.isDirectory).foreach(f => getAllLeafNodeDirectories(f.getPath))
      }
    }

    getAllLeafNodeDirectories(dirPath)
    accum.toList

  }

  override def listDirectoriesWithoutRecursion(dir: String, recursive: Boolean): List[String] = {

    val hadoopConf = sc.hadoopConfiguration
    val dirPath = new Path(dir)
    val filesystem = FileSystem.get(dirPath.toUri, hadoopConf)

    // List the sub directories under this directory. If there is a list recurse each one. If not, you have found
    // what you need. Add it to the list
    val accum = filesystem.listStatus(dirPath).filter(_.isDirectory).map(f => f.getPath.toString)
    accum.toList
  }

  override def listFilePathAndName(dir: String, recursive: Boolean = true): List[(String, String)] = {
    val hadoopConf = sc.hadoopConfiguration
    val dirPath = new Path(dir)
    val fs = FileSystem.get(dirPath.toUri, hadoopConf)

    def isDirectoryWithPartFiles(path: Path): Boolean = {
      val filesInDir = listFilenames(path.getName, false)
      val hasPartFiles = filesInDir.filter(p => p matches ".*part-.*").size > 0
      hasPartFiles
    }

    try {
      val statusIter = fs.listFiles(dirPath, recursive)
      val filenames = {
        val filenameBuffer = new ListBuffer[(String, String)]
        while (statusIter.hasNext) {
          val status = statusIter.next
          if (!status.isDirectory) {
              if (!(status.getPath.getName matches ".*_SUCCESS|.*part-.*")|| (status.getPath.getParent.toString.contains(Constants.uniqueDirNameAcceptance)) && (!status.getPath().getParent().toString.contains("_spark_metadata"))
                || (status.getPath.getParent.toString.contains(Constants.uniqueDirNameReject)) && (!status.getPath().getParent().toString.contains("_spark_metadata"))) {
                filenameBuffer.append((status.getPath.toString, status.getPath.getName))
              }
              else {
                filenameBuffer.append((status.getPath.getParent.toString, status.getPath.getParent.getName))
              }
          }
        }
        filenameBuffer.toList.distinct
      }
      filenames
    }
    catch {
      case exception: Exception => {
        logger.warn(exception.getMessage, exception)
        List.empty
      }
    }
    finally {
      fs.close()
    }
  }

  /**
   * Method to rename / move a file from one name / directory to another
   *
   * @param from Name of the source file to rename / move
   * @param to   What does the new file need to be called / moved to?
   */
  override def moveFile(from: String, to: String): Boolean = {
    if (!from.equals(to))
      return moveFileInternal(from, to)
    else true
  }

  /**
   * Method to rename / move a file from one name / directory to another
   *
   * @param from Name of the source file to rename / move
   * @param to   What does the new file need to be called / moved to?
   */
  override def moveFileToDir(from: String, to: String): Boolean = {
    val hadoopConf = sc.hadoopConfiguration
    val fromPath = new Path(from)
    val toPath = new Path(to)
    val fs = FileSystem.get(fromPath.toUri, hadoopConf)
    if (!fs.exists(toPath))
      touch(to, isDir = true)

    val fileName = fromPath.getName
    val fullToPath = to + File.separator + fileName

    moveFileInternal(from, fullToPath)

  }

  def moveFileInternal(from: String, to: String): Boolean = {
    val hadoopConf = sc.hadoopConfiguration
    val fromPath = new Path(from)
    val toPath = new Path(to)
    val fromFs = FileSystem.get(fromPath.toUri,hadoopConf)
    val toFs = FileSystem.get(toPath.toUri,hadoopConf)

    // FileSystem.rename is inconsistent with directories -- if the
    // dst (foo/a) exists and is a dir, and the src (foo/b) is a dir,
    // it moves the src into the dst dir resulting in (foo/a/b).  If
    // the dst does not exist, and the src a dir, src becomes dst. (foo/b)
    val retVal = fromFs.rename(fromPath, toPath)
    if (retVal)
      logger.debug(escapeJava("Moved file from " + from + " to " + to))
    else
      logger.error(escapeJava("Error in moving file from " + from + " to " + to))
    retVal
  }

  override def isDirAccessible(filePath: String, readWriteFlag: Int): Boolean = {
    val hadoopConfig = sc.hadoopConfiguration
    val path = new Path(filePath)
    val fs = FileSystem.newInstance(path.toUri, hadoopConfig)
    try {
      val isDir = fs.getFileStatus(path).isDirectory
      val dirAccessUserAction = fs.getFileStatus(path).getPermission.getUserAction
      val hasPermissions = {
        if ((readWriteFlag & OxygenFileUtils.IS_WRITABLE) == OxygenFileUtils.IS_WRITABLE)
          dirAccessUserAction.implies(FsAction.WRITE)
        else
          fs.getFileStatus(path).getPermission.getUserAction.implies(FsAction.READ)
      }
      // Is this a directory and has the appropriate permissions (WRITE implies readable)
      (exists(filePath) && isDir && hasPermissions)
    }
    catch {
      case _: AccessControlException => false
    }
    finally {
      fs.close()
    }
  }

  override def isDirReadable(path: String): Boolean = {
    isDirAccessible(path, OxygenFileUtils.IS_READABLE)
  }

  override def isDirWritable(path: String): Boolean = {
    isDirAccessible(path, OxygenFileUtils.IS_WRITABLE)
  }

  override def exists(path: String): Boolean = {
    val hadoopConfig = appContext.sparkSession.sparkContext.hadoopConfiguration
    val fullPath = new Path(path)
    val fs = FileSystem.get(fullPath.toUri, hadoopConfig)
    fs.exists(fullPath)
  }

  override def touch(fullFilePath: String, isDir: Boolean): Boolean = {
    val hadoopConfig = sc.hadoopConfiguration
    val path = new Path(fullFilePath)
    val fs = FileSystem.get(path.toUri, hadoopConfig)
    if (isDir)
      fs.mkdirs(path)
    else
      fs.createNewFile(path)

  }

  def writeToFile(fileName: String, in: InputStream): Unit = {
    val hadoopConfig = sc.hadoopConfiguration
    val path = new Path(fileName)
    val fs = FileSystem.get(path.toUri, hadoopConfig)
    val out = fs.create(path)
    using(out){out => IOUtils.copyBytes(in, out, 4096, true)}
    out.close()
  }

  override def writeStringToFile(text:String, file:String): Unit = {
    val hadoopConfig = sc.hadoopConfiguration
    val path = new Path(file)
    val fs = FileSystem.get(path.toUri, hadoopConfig)
    if (fs.exists(path)) fs.delete(path, true)
    Option(text).filterNot {
      _.isEmpty
    } foreach { s =>
      try {
        val inputBytes = s.getBytes(StandardCharsets.UTF_8)
        val inputStream = new ByteArrayInputStream(inputBytes)
        val outputStream: FSDataOutputStream = fs.create(path)
        IOUtils.copyBytes(inputStream, outputStream, inputBytes.size, true)
      } catch {
        case e: IOException => logger.error(e.getMessage)
      }
    }
  }

  override def deleteFileOrFolder(fullPath: String, recursive: Boolean = true): Unit = {
    if (fullPath != null && !fullPath.isEmpty) {
      val hadoopConfig = sc.hadoopConfiguration
      val path = new Path(fullPath)
      val fs = FileSystem.get(path.toUri, hadoopConfig)
      fs.delete(path, recursive)
    }
  }

  override def copyFileOrFolder(source:String, target:String): Unit = {
    val conf = sc.hadoopConfiguration
    val srcPath = new Path(source)
    val dstPath = new Path(target)
    FileUtil.copy(
      FileSystem.get(srcPath.toUri, conf),
      srcPath,
      FileSystem.get(dstPath.toUri, conf),
      dstPath,
      false,
      conf
    )
  }

  def retrieveFilesToExclude(filesListedIn: String, errorString: String = "No files found to exclude"): List[String] = {
    val filenameBuffer = new ListBuffer[String]
    val hadoopConfig = sc.hadoopConfiguration
    val filesystem = FileSystem.get(new Path(filesListedIn).toUri, hadoopConfig)
    for (elem <- listFilenames(filesListedIn, true) if !elem.isEmpty) {
      val path = new Path(elem)
      val fileToReadBuffer = new BufferedReader(new InputStreamReader(filesystem.open(path)))
      // Make sure .close() is called on fileToReadBuffer. The using() macro will do the job
      using(fileToReadBuffer) {
        fb => {
          var line: String = null
          while ({
            line = fb.readLine();
            line != null
          })
            filenameBuffer.append(line)
        }
      }
    }
    if (filenameBuffer.isEmpty)
      logger.info(errorString)
    filenameBuffer.toList
  }

  override def getSortedFiles(parentDir: String): ListBuffer[String] = {
    val hadoopConfig = sc.hadoopConfiguration
    val path = new Path(parentDir)
    val fs = FileSystem.get(path.toUri, hadoopConfig)
    val remoteIt = fs.listLocatedStatus(path)
    val fileList: ListBuffer[String] = new ListBuffer[String]
    while (remoteIt.hasNext) {
      fileList += remoteIt.next().getPath.toUri.getPath
    }
    val ret = fileList.sorted
    logger.debug("Sorted list of files in " + parentDir + " is " + ret.map(x => x + "\n"))
    ret
  }

  override def removeFilteredDir(parentPath: String, filter: String, pathSeparator : String): Unit = {
    val hadoopConfig = sc.hadoopConfiguration
    val path = new Path(parentPath)
    val fs = FileSystem.get(path.toUri, hadoopConfig)
    val fileStatusArray: Array[FileStatus] = fs.globStatus(new Path(parentPath + pathSeparator + filter))
    logger.info("Removing files matching pattern " + parentPath + pathSeparator + filter)
    if (fileStatusArray!= null &&  !fileStatusArray.isEmpty)
      fileStatusArray.foreach(f => deleteFileOrFolder(f.getPath.toUri.getPath))
  }

  // Method 1: List all directories to the last sub-directory for a given directory name. Return empty array if nothing found
  // Methdo 2: List all directories and files to the last sub-directory and file for a given director name. Return empty array if nothing found
  // Method 3: List all directories and sub-directories which contain the filter word
  // Method 4: List all files within sub-directories which contain the filter word
  // Method 5: List all leaf node sub-directories for a given directory name
  def list(dirOrFileName: Path, recursive: Boolean, filter: OxygenFilter): Array[Path] = {

    val hadoopConfig = sc.hadoopConfiguration
    val fs = FileSystem.get(dirOrFileName.toUri,hadoopConfig)
    val listOfDirsToReturn = new ListBuffer[Path]

    def listAll(path: Path, recursive: Boolean): ListBuffer[Path] = {
      // Retrieve the list of directories / files to query with filter
      val theList = fs.listStatus(path, filter)
      // If recursive is true, go to each directory and further look for files / directories which match the criteria.
      for (eachFile <- theList) {
        if (recursive && eachFile.isDirectory) {
          listAll(eachFile.getPath, recursive)
        }
      }
      if (fs.getFileStatus(path).isDirectory)
        listOfDirsToReturn.append(path)
      listOfDirsToReturn
    }
    listAll(dirOrFileName, recursive).toArray
  }

  override def deleteDirIfEmpty(dir: String) = {
    val hadoopConfig = sc.hadoopConfiguration
    val dirPath = new Path(dir)
    val fs = FileSystem.get(dirPath.toUri, hadoopConfig)
    if (fs.exists(dirPath)) {
      if (fs.listStatus(dirPath).isEmpty) {
        logger.info("Deleting empty directory: " + dirPath.toString)
        deleteFileOrFolder(dirPath.toString)
      }
    }
  }

  class OxygenFilter(regex: String) extends PathFilter {
    val pattern = Pattern.compile(regex)

    override def accept(path: Path): Boolean = {
      val matcher = pattern.matcher(path.toString)
      return matcher.matches()
    }

    override def toString: String = regex
  }

  override def createDirectory(dirPath: String): Unit = {
    try {
      val hadoopConfig = sc.hadoopConfiguration
      val path = new Path(dirPath)
      val fs = FileSystem.get(path.toUri, hadoopConfig)
      fs.mkdirs(path)
    } catch {
      case e: Exception => logger.warn(s"Failed to create Directory : ${e.getMessage}")
    }
  }

  override def getNewFilesToProcess(dataHomes: List[String]): List[(String, String)] = {
    val incomingFiles = dataHomes.flatMap(home => {
      listFilePathAndName(home).filter { case (fullPath, fileName) => (!fileName.contains(".inprocess") && !fullPath.contains("_spark_metadata")) }
    })
    incomingFiles
  }

  //Use with caution - To be improved
  override def readFileContentAsString(path: String): String = {
    val hadoopConfig = sc.hadoopConfiguration
    val filePath = new Path(path)
    val filesystem = FileSystem.get(filePath.toUri, hadoopConfig)
    readFileContentAsString(path, filesystem)
  }

  override private[oxygen] def isFileProcessed(processFilesStorageLocation: String, fileToCheck: String): Boolean = {
    val hadoopConfig = sc.hadoopConfiguration
    val path = new Path(processFilesStorageLocation)
    val filesystem = FileSystem.get(path.toUri, hadoopConfig)
    val fileList = listFilePathAndName(processFilesStorageLocation, false).filter(name => name._2.matches("\\d*$"))
    for (elem <- fileList if !elem._1.isEmpty) {
      try {
        val processUpdate = JsonUtils.fromJson(readFileContentAsString(elem._1, filesystem), classOf[ProcessUpdate])
        if (processUpdate.location().equals(fileToCheck))
          return true
      } catch {
        case e: Exception => {
          logger.warn("Unable to parse a processFile", e)
          false
        }
      }
    }
    false
  }

  private[this] def readFileContentAsString(path: String, filesystem: FileSystem): String = {
    Try({
      val filenameBuffer = new ListBuffer[String]
      val fileToReadBuffer = new BufferedReader(new InputStreamReader(filesystem.open(new Path(path))))
      using(fileToReadBuffer) {
        fb => {
          var line: String = null
          while ( {
            line = fb.readLine();
            line != null
          })
            filenameBuffer.append(line)
        }
      }
      filenameBuffer.toList.mkString("\n")
    }
    ).getOrElse(null)
  }

  override def getSize(path: String): Long = {
    val hadoopConfig = sc.hadoopConfiguration
    val filePath = new Path(path)
    val filesystem = FileSystem.get(filePath.toUri, hadoopConfig)
    filesystem.getContentSummary(filePath).getLength
  }
}

object OxygenFileUtils extends Serializable {

  val IS_READABLE = 1
  val IS_WRITABLE = 2

  def create[T <: AppContext](appContext: T): FileUtils = {
    if (appContext.isInstanceOf[SparkOxygenAppContext])
      new SparkFileUtils(appContext.asInstanceOf[SparkOxygenAppContext])
    else
      new LocalFileUtils(appContext.asInstanceOf[OxygenAppContext])
  }
}
