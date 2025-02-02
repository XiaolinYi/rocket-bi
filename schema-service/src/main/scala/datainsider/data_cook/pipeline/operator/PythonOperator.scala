package datainsider.data_cook.pipeline.operator

import com.twitter.inject.Logging
import datainsider.client.domain.query.SqlQuery
import datainsider.data_cook.domain.Ids.OrganizationId
import datainsider.data_cook.domain.operator.TableConfiguration
import datainsider.data_cook.pipeline.exception.{InputInvalid, OperatorException}
import datainsider.data_cook.pipeline.operator.Operator.OperatorId
import datainsider.data_cook.service.table.EtlTableService
import datainsider.data_cook.util.{ProcessUtils, StringUtils}
import datainsider.ingestion.domain.TableSchema
import datainsider.ingestion.domain.Types.TblName
import datainsider.ingestion.service.SchemaService
import datainsider.ingestion.util.Implicits.FutureEnhance
import datainsider.profiler.Profiler

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.TimeoutException

case class PythonOperator(
    id: OperatorId,
    code: String,
    destTableConfiguration: TableConfiguration
) extends TableResultOperator

case class PythonOperatorExecutor(
    etlTableService: EtlTableService,
    schemaService: SchemaService,
    pythonTemplate: String,
    baseDir: String,
    clickhouseHost: String,
    clickhousePort: String,
    clickhouseUser: String,
    clickhousePass: String,
    queryLimit: Int = 100000,
    queryRetries: Int = 2,
    connectTimeoutSec: Int = 30,
    // default is 2 hour
    executeTimeoutMs: Long = 7200000
) extends Executor[PythonOperator]
    with Logging {
  @throws[InputInvalid]
  @throws[OperatorException]
  override def process(operator: PythonOperator, context: ExecutorContext): OperatorResult = {
    Profiler(s"[Executor] ${getClass.getSimpleName}.process") {
      var pythonFile: File = null
      try {
        ensureInput(operator, context.mapResults)
        val parentSchema: TableSchema = getParentTableSchema(operator, context.mapResults).get
        val tmpTblName: String = TableSchema.buildTemporaryTblName(operator.destTableConfiguration.tblName)
        pythonFile = preparePythonFile(parentSchema, operator.code, tmpTblName)
        executePython(pythonFile)
        val tableSchema: TableSchema = createView(context.orgId, context.jobId, parentSchema.dbName, tmpTblName, operator.destTableConfiguration)
        TableResult(operator.id, tableSchema)
      } finally {
        if (pythonFile != null) {
          deleteFile(pythonFile)
        }
      }
    }
  }

  private def executePython(pythonFile: File): Unit = {
    val logBuffer = new StringBuffer()
    try {
      logger.info(s"Executing python code python")
      val exitValue = ProcessUtils.execute(s"python3 ${pythonFile.getAbsolutePath}", executeTimeoutMs, logBuffer)
      if (exitValue == 0) {
        logger.info(s"Python code executed successfully: $pythonFile")
      } else {
        val errorMsg: String = s"Python code execution failed, cause ${logBuffer.toString}"
        logger.error(errorMsg)
        throw new OperatorException(errorMsg)
      }
    } catch {
      case ex: OperatorException => throw ex
      case ex: TimeoutException =>
        val errorMsg: String = s"Python code execution timeout, maximum time is ${executeTimeoutMs} milliseconds"
        logger.error(errorMsg)
        throw new OperatorException(errorMsg, ex)
      case ex: Throwable => {
        logger.error(s"Python code execution failed: ${ex.getMessage}")
        throw new OperatorException(s"Python code execution failed: ${ex.getMessage}")
      }
    }
  }

  private def preparePythonFile(parentTableSchema: TableSchema, processFunction: String, tempTblName: String): File = {
    val dbName: String = parentTableSchema.dbName
    val tblName: String = parentTableSchema.name

    val pythonPath = Paths.get(baseDir, "python", s"${dbName}_${System.currentTimeMillis()}.py")
    logger.info(s"Preparing python file in path ${pythonPath.toAbsolutePath}")

    val pythonCode = StringUtils.format(
      pythonTemplate,
      Map(
        "code" -> processFunction,
        "host" -> clickhouseHost,
        "port" -> clickhousePort,
        "user" -> clickhouseUser,
        "password" -> clickhousePass,
        "database" -> dbName,
        "table" -> tblName,
        "dest_database" -> dbName,
        "dest_table" -> tempTblName,
        "query_limit" -> queryLimit,
        "query_retries" -> queryRetries,
        "connect_timeout_seconds" -> connectTimeoutSec
      )
    )
    val file = new File(pythonPath.toString)
    if (!file.getParentFile.exists()) {
      file.getParentFile.mkdirs()
    }
    Files.write(file.toPath, pythonCode.getBytes(StandardCharsets.UTF_8))

    logger.info("Write python file successfully")
    file
  }

  private def deleteFile(file: File): Unit = {
    try {
      logger.info(s"Deleting file: ${file.getAbsolutePath}")
      file.delete()
    } catch {
      case ex: Throwable => logger.error(s"Failed to delete file: ${file.getAbsolutePath}", ex)
    }
  }

  /**
    * create view table schema for tmp table
    */
  private def createView(
      orgId: OrganizationId,
      jobId: Long,
      dbName: String,
      tmpTblName: String,
      destinationConfig: TableConfiguration
  ): TableSchema = {
    val selectQuery: SqlQuery = SqlQuery(s"select * from `$dbName`.`$tmpTblName`")
    val tableSchema: TableSchema = etlTableService.creatView(orgId, jobId, selectQuery, destinationConfig).syncGet()
    tableSchema
  }

  private def ensureInput(operator: PythonOperator, mapResults: mutable.Map[OperatorId, OperatorResult]): Unit = {

    if (operator.parentIds.size != 1) {
      throw InputInvalid("only take one input for sql operator")
    }

    val parentTable: Option[TableSchema] = getParentTableSchema(operator, mapResults)
    if (parentTable.isEmpty) {
      throw InputInvalid("missing input for sql operator")
    }

  }

  private def getParentTableSchema(
      operator: PythonOperator,
      mapResults: mutable.Map[OperatorId, OperatorResult]
  ): Option[TableSchema] = {
    mapResults.get(operator.parentIds.head).flatMap(_.getData[TableSchema]())
  }

}
